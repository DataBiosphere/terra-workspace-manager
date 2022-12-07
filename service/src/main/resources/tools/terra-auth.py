#!/usr/bin/env python3

import argparse
import google.auth
import json
import os
import subprocess
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

def enumerate_workspace_ids(session):
    workspace_ids = []
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
            workspace_ids.append(workspace['id'])
            received_count = received_count + 1
            offset = offset + 1
        if received_count < request_count:
            more = False
    return workspace_ids

def enumerate_workspace_resources(session, workspace_id, type):
        out_resources = []
        request_count = 10
        offset = 0
        more = True
        while more:
            response = session.get(f'{WSM_API_ENDPOINT}/{workspace_id}/resources',
                params={'request': request_count, 'offset': offset, 'resource': type})

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
    response = session.get(f'{WSM_API_ENDPOINT}/{workspace_id}/resources/controlled/aws/sagemaker-notebooks/{notebook_id}/getCredential', params={'accessScope': access, 'credentialDuration': 900})
    if not response.ok:
        print(f'ERROR: Getting notebook cred failed with status {response.status_code}', file=sys.stderr)
        sys.exit(GET_AWS_CRED_FAILED)
    return response.content.decode('ascii')

def get_bucket_cred(session, workspace_id, bucket_id, access):
    response = session.get(f'{WSM_API_ENDPOINT}/{workspace_id}/resources/controlled/aws/buckets/{bucket_id}/getCredential', params={'accessScope': access, 'credentialDuration': 3600})
    if not response.ok:
        print(f'ERROR: Getting notebook cred failed with status {response.status_code}', file=sys.stderr)
        sys.exit(GET_AWS_CRED_FAILED)
    return response.content.decode('ascii')

def find_notebook_metadata(session):
    retVal = {
        'WorkspaceId': None,
        'ResourceId': None,
        'DefaultBucketId': None,
        'DefaultBucketAccess': None
    }
    found = False

    resource_name = get_notebook_resource_name()
    workspace_ids = enumerate_workspace_ids(session)
    for workspace_id in workspace_ids:
        resources = enumerate_workspace_resources(session, workspace_id, 'AWS_SAGEMAKER_NOTEBOOK')
        for resource in resources:
            notebook_attributes = resource['resourceAttributes']['awsSagemakerNotebook']
            if notebook_attributes['instanceId'] == resource_name:
                found = True
                retVal['WorkspaceId'] = resource['metadata']['workspaceId']
                retVal['ResourceId'] = resource['metadata']['resourceId']

                default_bucket = notebook_attributes['defaultBucket']
                if default_bucket is not None:
                    retVal['DefaultBucketId'] = default_bucket['bucketId']
                    retVal['DefaultBucketAccess'] = default_bucket['accessScope']

    return retVal

def get_notebook_metadata(session):
    if(os.path.exists(NOTEBOOK_METADATA_FILE)):
        with open(NOTEBOOK_METADATA_FILE, 'r') as f:
            return json.load(f)
    else:
        metadata = find_notebook_metadata(session)

        if metadata['WorkspaceId'] is None or metadata['ResourceId'] is None:
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

    bucket_resources = enumerate_workspace_resources(session, notebook_metadata['WorkspaceId'], 'AWS_BUCKET')
    for bucket_resource in bucket_resources:
        name = bucket_resource['metadata']['name']
        id = bucket_resource['metadata']['resourceId']
        if len(configs) > 0:
            configs += '\n'
        configs += f'''[profile bucket-{name}{suffix}]
region = us-east-1
credential_process = "{os.path.realpath(__file__)}" --bucket {id} --access {access}\n'''

        if notebook_metadata['DefaultBucketId'] == id and notebook_metadata['DefaultBucketAccess'] == access:
            configs += f'''\n[default]
region = us-east-1
credential_process = "{os.path.realpath(__file__)}" --bucket {id} --access {access}\n'''

        return configs

def main():
    args = parse_args()
    session = get_authorized_session()
    notebook_metadata = get_notebook_metadata(session)

    if args.configure:
        config = get_notebook_config('profile this-notebook', notebook_metadata)
        config = get_bucket_configs(config, session, notebook_metadata, 'WRITE_READ')
        config = get_bucket_configs(config, session, notebook_metadata, 'READ_ONLY')
        if config.find('[default]') == -1:
            config += '\n' + get_notebook_config('default', notebook_metadata)
        with open(f'{os.path.expanduser("~")}/.aws/config', 'w') as f:
            f.write(config)
    elif args.notebook:
        print(get_notebook_cred(session, notebook_metadata['WorkspaceId'], notebook_metadata['ResourceId'], args.access))

    elif args.bucket:
        print(get_bucket_cred(session, notebook_metadata['WorkspaceId'], args.bucket, args.access))

if __name__ == "__main__":
    main()
