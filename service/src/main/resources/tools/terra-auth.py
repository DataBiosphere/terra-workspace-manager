#!/usr/bin/env python3

import argparse
import google.auth
import json
import os
import sys
import warnings

from google.auth.exceptions import DefaultCredentialsError
from google.auth.transport.requests import AuthorizedSession

BAD_GOOGLE_CRED = 1
GET_AWS_CRED_FAILED = 2
LIST_WORKSPACES_FAILED = 3
LIST_RESOURCES_FAILED = 4
NO_INSTANCE_METADATA = 5
NOTEBOOK_NOT_FOUND = 6

WSM_API_ENDPOINT = 'https://terra-mc-feature-dev-wsm.api.verily.com/api/workspaces/v1'
INSTANCE_METADATA_FILE = '/opt/ml/metadata/resource-metadata.json'
NOTEBOOK_METADATA_FILE = f'{os.path.expanduser("~")}/.terra/notebook_metadata.json'

def parse_args():
    parser = argparse.ArgumentParser(description = 'Terra AWS Auth Helper')

    parser.add_argument('--whoami', action='store_true', required=False)

    parser.add_argument('--configure', action='store_true', required=False)

    parser.add_argument('--bucket', action='store', required=False,
        help='Terra Bucket to request access credential for.')

    parser.add_argument('--notebook', action='store_true', required=False,
        help='Terra Notebook to request access credential for.')

    parser.add_argument('--access', action='store', required=False, default='WRITE_READ')

    return parser.parse_args()

def get_authorized_session():
    try:
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
            credentials, project_id = google.auth.default()
    except DefaultCredentialsError:
        print("ERROR: Application Default Credentials required, please run 'gcloud auth application-default login' and log in with your Terra user ID.", file=sys.stderr)
        sys.exit(BAD_GOOGLE_CRED)

    return AuthorizedSession(credentials)

def get_notebook_resource_attribute(attribute):
    if not os.path.exists(INSTANCE_METADATA_FILE):
        print(f'ERROR: Notebook resource file "{INSTANCE_METADATA_FILE}" does note exist.', file=sys.stderr)
        sys.exit(NO_INSTANCE_METADATA)

    with open(INSTANCE_METADATA_FILE, 'r') as f:
        resource_metadata = json.load(f)
        return resource_metadata[attribute]

def get_notebook_resource_name():
    return get_notebook_resource_attribute('ResourceName')

def get_notebook_resource_arn():
    return get_notebook_resource_attribute('ResourceArn')

def enumerate_workspaces(session):
    workspaces_out = []
    request_count = 10
    offset = 0
    more = True
    while more:
        response = session.get(WSM_API_ENDPOINT, params={'request': request_count, 'offset': offset})
        if not response.ok:
            print(f'ERROR: Listing workspaces failed with status {response.status_code}', file=sys.stderr)
            sys.exit(LIST_WORKSPACES_FAILED)
        workspaces = json.loads(response.content)
        received_count = 0
        for workspace in workspaces['workspaces']:
            workspaces_out.append(workspace)
            received_count = received_count + 1
            offset = offset + 1
        if received_count < request_count:
            more = False
    return workspaces_out

def enumerate_workspace_resources(session, workspace_id, resource_type):
      out_resources = []
      request_count = 10
      offset = 0
      more = True
      while more:
          response = session.get(f'{WSM_API_ENDPOINT}/{workspace_id}/resources',
              params={'request': request_count, 'offset': offset, 'resource': resource_type})

          if not response.ok:
              print(f'ERROR: Listing resources failed with status {response.status_code}', file=sys.stderr)
              sys.exit(LIST_RESOURCES_FAILED)

          resources = json.loads(response.content)
          received_count = 0
          for resource in resources['resources']:
              out_resources.append(resource)
              received_count = received_count + 1
              offset = offset + 1
          if received_count < request_count:
              more = False
      return out_resources

def get_notebook_cred(session, workspace_id, notebook_id, access):
    response = session.get(f'{WSM_API_ENDPOINT}/{workspace_id}/resources/controlled/aws/sagemaker-notebooks/{notebook_id}/credential', params={'accessScope': access, 'credentialDuration': 900})
    if not response.ok:
        print(f'ERROR: Getting notebook cred failed with status {response.status_code}', file=sys.stderr)
        sys.exit(GET_AWS_CRED_FAILED)
    return response.content.decode('ascii')

def get_bucket_cred(session, workspace_id, bucket_id, access):
    response = session.get(f'{WSM_API_ENDPOINT}/{workspace_id}/resources/controlled/aws/buckets/{bucket_id}/credential', params={'accessScope': access, 'credentialDuration': 3600})
    if not response.ok:
        print(f'ERROR: Getting notebook cred failed with status {response.status_code}', file=sys.stderr)
        sys.exit(GET_AWS_CRED_FAILED)
    return response.content.decode('ascii')

def find_notebook_metadata(session):
    retVal = {
        'Workspace': None,
        'Notebook': None,
        'DefaultBucketId': None,
        'DefaultBucketAccess': None
    }

    resource_name = get_notebook_resource_name()
    workspaces = enumerate_workspaces(session)
    for workspace in workspaces:
        workspace_id = workspace['id']
        resources = enumerate_workspace_resources(session, workspace_id, 'AWS_SAGEMAKER_NOTEBOOK')
        for resource in resources:
            notebook_attributes = resource['resourceAttributes']['awsSagemakerNotebook']
            if notebook_attributes['instanceId'] == resource_name:
                retVal['Workspace'] = workspace
                retVal['Notebook'] = resource

                default_bucket = notebook_attributes['defaultBucket']
                if default_bucket is not None:
                    retVal['DefaultBucketId'] = default_bucket['bucketId']
                    retVal['DefaultBucketAccess'] = default_bucket['accessScope']

    return retVal

def get_notebook_metadata(session):
    if(os.path.exists(NOTEBOOK_METADATA_FILE)):
        with open(NOTEBOOK_METADATA_FILE, 'r') as f:
            metadata = json.load(f)
            if 'Workspace' in metadata and 'Notebook' in metadata:
                return metadata

    metadata = find_notebook_metadata(session)

    if metadata['Workspace'] is None or metadata['Notebook'] is None:
        print("Workspace and Notebook Resource ID could not be resolved.", file=sys.stderr)
        sys.exit(NOTEBOOK_NOT_FOUND)

    with open(NOTEBOOK_METADATA_FILE, 'w') as f:
        json.dump(metadata, f, indent=4)

    return metadata

def get_notebook_config(label, notebook_metadata):
    return f'''[{label}]
region = us-east-1
credential_process = "{os.path.realpath(__file__)}" --notebook --access WRITE_READ\n'''

def get_bucket_configs(config, session, notebook_metadata, access):
    configs = config
    suffix = '-ro' if access == 'READ_ONLY' else ''

    workspace_id = notebook_metadata['Workspace']['id']
    bucket_resources = enumerate_workspace_resources(session, workspace_id, 'AWS_BUCKET')
    for bucket_resource in bucket_resources:
        name = bucket_resource['metadata']['name']
        resourceId = bucket_resource['metadata']['resourceId']
        if len(configs) > 0:
            configs += '\n'
        configs += f'''[profile bucket-{name}{suffix}]
region = us-east-1
credential_process = "{os.path.realpath(__file__)}" --bucket {resourceId} --access {access}\n'''

        if notebook_metadata['DefaultBucketId'] == id and notebook_metadata['DefaultBucketAccess'] == access:
            configs += f'''\n[default]
region = us-east-1
credential_process = "{os.path.realpath(__file__)}" --bucket {resourceId} --access {access}\n'''

    return configs

def print_bucket_details(bucket, default, write_read):
    bucket_metadata = bucket['metadata']
    bucket_attributes = bucket['resourceAttributes']['awsBucket']

    default_string = ''
    profile_string = ''
    ro_profile_string = ''

    if default:
        default_string = f' (DEFAULT, {"READ/WRITE" if write_read else "READ ONLY"})'

        if write_read:
            profile_string = 'default, '

        else:
            ro_profile_string = 'default, '

    print(f'            Terra Storage Bucket Name: {bucket_metadata["name"]}{default_string}')
    print(f'     Terra Storage Bucket Description: {bucket_metadata["description"]}')
    print(f'             Terra Storage Bucket URI: s3://{bucket_attributes["s3BucketName"]}/{bucket_attributes["prefix"]}/')
    print(f'           AWS SDK/CLI Access Profile: {profile_string}bucket-{bucket_metadata["name"]}')
    print(f' Read Only AWS SDK/CLI Access Profile: {ro_profile_string}bucket-{bucket_metadata["name"]}-ro')
    print('')

def print_details(notebook_metadata, session):
    workspace = notebook_metadata["Workspace"]
    print('')
    print(f'                 Terra Workspace Name: {workspace["displayName"]}')
    print(f'                   Terra Workspace ID: {workspace["userFacingId"]}')
    print(f'                 Terra Workspace UUID: {workspace["id"]}')
    print(f'           Terra Workspace Descrption: {workspace["description"]}')
    print('')
    notebook = notebook_metadata["Notebook"]
    print(f'        Terra SageMaker Notebook Name: {notebook["metadata"]["name"]}')
    print(f'        Terra SageMaker Notebook UUID: {notebook["metadata"]["resourceId"]}')
    print(f' Terra SageMaker Notebook Instance ID: {notebook["resourceAttributes"]["awsSagemakerNotebook"]["instanceId"]}')
    print('')

    workspace_id = workspace['id']
    bucket_resources = enumerate_workspace_resources(session, workspace_id, 'AWS_BUCKET')

    # Loop once and only print default (so it shows up first)
    for bucket in bucket_resources:
        if bucket['metadata']['resourceId'] == notebook_metadata['DefaultBucketId']:
            print_bucket_details(bucket, True, notebook_metadata['DefaultBucketAccess'] == 'WRITE_READ')

    # Loop again and only print NOT default
    for bucket in bucket_resources:
        if bucket['metadata']['resourceId'] != notebook_metadata['DefaultBucketId']:
           print_bucket_details(bucket, False, False)

    print('')

def main():
    args = parse_args()
    session = get_authorized_session()
    notebook_metadata = get_notebook_metadata(session)
    workspace_id = notebook_metadata['Workspace']['id']

    if args.whoami:
        print_details(notebook_metadata, session)

    if args.configure:
        config = get_notebook_config('profile this-notebook', notebook_metadata)
        config = get_bucket_configs(config, session, notebook_metadata, 'WRITE_READ')
        config = get_bucket_configs(config, session, notebook_metadata, 'READ_ONLY')
        if config.find('[default]') == -1:
            config += '\n' + get_notebook_config('default', notebook_metadata)
        with open(f'{os.path.expanduser("~")}/.aws/config', 'w') as f:
            f.write(config)
    elif args.notebook:
        notebook_id = notebook_metadata['Notebook']['metadata']['resourceId']
        print(get_notebook_cred(session, workspace_id, notebook_id, args.access))

    elif args.bucket:
        print(get_bucket_cred(session, workspace_id, args.bucket, args.access))


if __name__ == "__main__":
    main()
