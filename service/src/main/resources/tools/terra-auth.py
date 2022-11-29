#!/usr/bin/env python3

import argparse
import boto3
import google.auth
import json
import os
import subprocess
import sys
import warnings

from google.auth.exceptions import DefaultCredentialsError
from google.auth.transport.requests import AuthorizedSession

WSM_API_ENDPOINT = 'https://terra-mc-feature-dev-wsm.api.verily.com/api/workspaces/v1'
INSTANCE_METADATA_FILE = '/opt/ml/metadata/resource-metadata.json'
NOTEBOOK_METADATA_FILE = f'{os.path.expanduser("~")}/.terra_notebook_metadata.json'

def parse_args():
    parser = argparse.ArgumentParser(description = 'Terra AWS Auth Helper')

    parser.add_argument('--configure', action='store_true', required=False)

    parser.add_argument('--bucket', action='store', required=False,
        help='Terra Bucket to request access credential for.')

    parser.add_argument('--notebook', action='store_true', required=False,
        help='Terra Notebook to request access credential for.')

    return parser.parse_args()

def get_authorized_session():
    try:
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
            credentials, project_id = google.auth.default()
    except DefaultCredentialsError:
        sys.exit("ERROR: Application Default Credentials required, please run 'gcloud auth application-default login' and log in with your Terra user ID.")

    return AuthorizedSession(credentials)

def get_notebook_resource_name():
    with open(INSTANCE_METADATA_FILE, 'r') as f:
        resource_metadata = json.load(f)
        return resource_metadata['ResourceName']

def get_notebook_resource_arn():
    with open(INSTANCE_METADATA_FILE, 'r') as f:
        resource_metadata = json.load(f)
        return resource_metadata['ResourceArn']

def enumerate_workspace_ids(session):
    workspace_ids = []
    request_count = 10
    offset = 0
    more = True
    while more:
        response = session.get(WSM_API_ENDPOINT, params={'request': request_count, 'offset': offset})
        if not response.ok:
            sys.exit(f'ERROR: Listing workspaces failed with status {response.status_code}')
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
                sys.exit(f'ERROR: Listing resources failed with status {response.status_code}')

            resources = json.loads(response.content)
            received_count = 0
            for resource in resources['resources']:
                out_resources.append(resource)
                received_count = received_count + 1
                offset = offset + 1
            if received_count < request_count:
                more = False
        return out_resources

def get_notebook_cred(session, workspace_id, notebook_id):
    response = session.get(f'{WSM_API_ENDPOINT}/{workspace_id}/resources/controlled/aws/sagemaker-notebooks/{notebook_id}/getCredential', params={'accessScope': 'READ_ONLY', 'credentialDuration': 900})
    if not response.ok:
        sys.exit(f'ERROR: Getting notebook cred failed with status {response.status_code}')
    return response.content.decode('ascii')

def get_bucket_cred(session, workspace_id, bucket_id):
    response = session.get(f'{WSM_API_ENDPOINT}/{workspace_id}/resources/controlled/aws/buckets/{bucket_id}/getCredential', params={'accessScope': 'WRITE_READ', 'credentialDuration': 3600})
    if not response.ok:
        sys.exit(f'ERROR: Getting notebook cred failed with status {response.status_code}')
    return response.content.decode('ascii')

def get_notebook_tags(session, workspace_id, notebook_id):
    user_credentials = json.loads(get_notebook_cred(session, workspace_id, notebook_id))
    user_sagemaker_session = boto3.client('sagemaker',
              region_name='us-east-1',
              aws_access_key_id=user_credentials['AccessKeyId'],
              aws_secret_access_key=user_credentials['SecretAccessKey'],
              aws_session_token=user_credentials['SessionToken'])
    return user_sagemaker_session.list_tags(ResourceArn=get_notebook_resource_arn())


def find_notebook_metadata(session):
    retVal = {
        'WorkspaceId': None,
        'ResourceId': None,
        'DefaultBucketPrefix': None
    }
    found = False

    resource_name = get_notebook_resource_name()
    workspace_ids = enumerate_workspace_ids(session)
    for workspace_id in workspace_ids:
        resources = enumerate_workspace_resources(session, workspace_id, 'AWS_SAGEMAKER_NOTEBOOK')
        for resource in resources:
            if resource['resourceAttributes']['awsSagemakerNotebook']['instanceId'] == resource_name:
                found = True
                retVal['WorkspaceId'] = resource['metadata']['workspaceId']
                retVal['ResourceId'] = resource['metadata']['resourceId']

    if retVal['WorkspaceId'] is not None and retVal['ResourceId'] is not None:
        tags = get_notebook_tags(session, retVal['WorkspaceId'], retVal['ResourceId'])
        for tag in tags['Tags']:
            if tag['Key'] == 'terra_bucket':
                retVal['DefaultBucketPrefix'] = tag['Value']

    return retVal

def get_notebook_metadata(session):
    if(os.path.exists(NOTEBOOK_METADATA_FILE)):
        with open(NOTEBOOK_METADATA_FILE, 'r') as f:
            return json.load(f)
    else:
        metadata = find_notebook_metadata(session)
        with open(NOTEBOOK_METADATA_FILE, 'w') as f:
            json.dump(metadata, f)
        return metadata

def get_notebook_config(label, notebook_metadata):
    return f'''[{label}]
region = us-east-1
credential_process = "{os.path.realpath(__file__)}" --notebook\n'''

def get_bucket_configs(config, session, notebook_metadata):
    configs = config

    notebook_resources = enumerate_workspace_resources(session, notebook_metadata['WorkspaceId'], 'AWS_BUCKET')
    for notebook_resource in notebook_resources:
        name = notebook_resource['metadata']['name']
        id = notebook_resource['metadata']['resourceId']
        prefix = notebook_resource['resourceAttributes']['awsBucket']['prefix']
        if len(configs) > 0:
            configs += '\n'
        configs += f'''[profile bucket-{name}]
region = us-east-1
credential_process = "{os.path.realpath(__file__)}" --bucket {id}\n'''

        if notebook_metadata['DefaultBucketPrefix'] == prefix:
            configs += f'''\n[default]
region = us-east-1
credential_process = "{os.path.realpath(__file__)}" --bucket {id}\n'''

        return configs

def main():
    args = parse_args()
    session = get_authorized_session()
    notebook_metadata = get_notebook_metadata(session)

    if args.configure:
        config = get_notebook_config('profile this-notebook', notebook_metadata)
        config = get_bucket_configs(config, session, notebook_metadata)
        if config.find('[default]') == -1:
            config += '\n' + get_notebook_config('default', notebook_metadata)
        with open(f'{os.path.expanduser("~")}/.aws/config', 'w') as f:
            f.write(config)
    elif args.notebook:
        print(get_notebook_cred(session, notebook_metadata['WorkspaceId'], notebook_metadata['ResourceId']))

    elif args.bucket:
        print(get_bucket_cred(session, notebook_metadata['WorkspaceId'], args.bucket))

if __name__ == "__main__":
    main()
