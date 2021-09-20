#!/bin/bash
#
# A script for cleaning up a user's Sam resources, such as those not cleaned
# up after testing. This will attempt to delete all controlled (both shared and
# private) resources owned by a user in Sam, as well as all their workspaces.
#
# This script will attempt to delete all of the resources and workspaces a user
# has any access to, including ones the user does not have permission to delete
# (such as those owned by other test users, where the specified user is only
# a reader). This means some delete calls will fail with a 403 error and the
# message "You may not perform any of [DELETE] on
# {resource type}/{resource id}". This is expected and can be ignored.
#
# Input:
#   access_token: An access token used for calls to Sam. This script will delete
#   all controlled resources and workspaces in Sam that the user owns, so be
#   sure this belongs to the correct user.

access_token=$1
sam_url="https://sam.dsde-dev.broadinstitute.org/api"

# Workspaces must be deleted last as they are parents of other resource types.
# Sam will not allow us to delete a workspace which contains controlled resources.
for resource_type in "controlled-user-shared-workspace-resource" "controlled-user-private-workspace-resource" "workspace"; do
  echo "Cleaning up $resource_type"
  # Fetch resources belonging to this user and save their IDs.
  curl -X GET "$sam_url/resources/v2/$resource_type" \
  -H "accept: application/json" -H "Authorization: Bearer $access_token" \
  | jq -r ".[] | .resourceId" >$resource_type.txt
  # Prompt user for confirmation
  num_resources="$(wc -l <$resource_type.txt)"
  read -p "This will delete $num_resources resources of type $resource_type. Continue: y/N? " yn
  if [ "$yn" == "${yn#[Yy]}" ] ;then
    break
  fi

  # For each ID, call Sam to delete the resource.
  while read -r resource_id; do
    echo "Deleting $resource_id"
    curl -X DELETE "$sam_url/resources/v2/$resource_type/$resource_id" \
    -H "accept: */*" -H "Authorization: Bearer $access_token"
  done <$resource_type.txt
  # Clean up the intermediate file
  rm $resource_type.txt
done
