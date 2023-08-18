import argparse
import botocore
import boto3
import time
import uuid

class Stopwatch:

    time_format = "%Y-%m-%d %H:%M:%S%z"
    start_time = None
    instance_name = None
    label = None

    def start(this, instance_name, label):
        this.start_time = time.time()
        this.instance_name = instance_name
        this.label = label

    def stop(this):
        end_time = time.time()
        elapsed = end_time - this.start_time
        print(",".join([
          this.instance_name,
          this.label,
          time.strftime(this.time_format, time.gmtime(this.start_time)),
          time.strftime(this.time_format, time.gmtime(end_time)),
          str(elapsed)
        ]))
        return elapsed

stopwatch = Stopwatch()

def parse_args():
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--role_arn", help="ARN of IAM role notebook will run as.")

    parser.add_argument(
        "--kms_key", help="ID of KMS key to use for notebook attached storage.")

    parser.add_argument(
        "--lifecycle_config", help="Name of lifecycle config to attach to notebook.")

    parser.add_argument(
        "--instance_type",
        default = "ml.t3.medium",
        help="Name of lifecycle config to attach to notebook (default: 'ml.t3.medium')")

    parser.add_argument(
        "--volume_size",
        type=int,
        default=5,
        help='Notebook attached volume size in GiB (default: 5 GiB)'
    )

    parser.add_argument(
        "--time_limit",
        type=float,
        default=3600,
        help='Time limit in seconds to consider a failure and return non-zero status (default: 3600 sec).'
    )

    return parser.parse_args()

def wait_for_status(client, name, status):
    while True:
        response = client.describe_notebook_instance(NotebookInstanceName=name)
        if response["NotebookInstanceStatus"] == status:
            break
        time.sleep(30)

def create_and_wait(client, name, instance_type, volume_size, role_arn,
  kms_key, lifecycle_config):

    # Create the notebook
    stopwatch.start(name, "CREATE")
    client.create_notebook_instance(
        NotebookInstanceName = name,
        InstanceType = instance_type,
        VolumeSizeInGB = volume_size,
        RoleArn = role_arn,
        KmsKeyId = kms_key,
        LifecycleConfigName = lifecycle_config
    )
    elapsed = stopwatch.stop()

    # Wait for in-service
    stopwatch.start(name, "WAIT_READY")
    wait_for_status(client, name, "InService")
    return elapsed + stopwatch.stop()

def stop_and_delete(client, name):

    stopwatch.start(name, "STOP")
    response = client.stop_notebook_instance(NotebookInstanceName=name)
    elapsed = stopwatch.stop()

    stopwatch.start(name, "WAIT_STOPPED")
    wait_for_status(client, name, "Stopped")
    elapsed = elapsed + stopwatch.stop()

    stopwatch.start(name, "DELETE")
    client.delete_notebook_instance(NotebookInstanceName=name)
    elapsed = elapsed + stopwatch.stop()

    stopwatch.start(name, "WAIT_DELETED")

    # We may never see "Deleted" status, and may just observer the notebook
    # disappearing.  Handle this cleanly.
    try:
        wait_for_status(client, name, "Deleted")
    except botocore.exceptions.ClientError as e:
        error = e.response['Error']
        if not (error['Code'] == "ValidationException" and error['Message'] == "RecordNotFound"):
            raise

    return elapsed + stopwatch.stop()

def main():
    args = parse_args()
    client = boto3.client("sagemaker")
    name = f"sagemaker-repro-uuid-{uuid.uuid4()}".lower()

    elapsed = create_and_wait(client, name, args.instance_type, args.volume_size,
      args.role_arn, args.kms_key, args.lifecycle_config)

    elapsed = elapsed + stop_and_delete(client, name)

    if elapsed > args.time_limit:
        exit(-1)

if __name__ == "__main__":
    main()
