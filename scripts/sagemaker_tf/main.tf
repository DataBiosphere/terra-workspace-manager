terraform {
  required_version = "1.1.9"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.0"
    }
  }
}

variable "name_prefix" {
  type = string
  description = "Prefix to append to resource names for uniqueness."
}

################################################################################
#  IAM Role for Notebook Instance
################################################################################

resource "aws_iam_role" "notebook" {
  name               = "${var.name_prefix}ReproNotebookExecution"
  assume_role_policy = data.aws_iam_policy_document.assume_notebook.json
}

data "aws_iam_policy_document" "assume_notebook" {
  statement {

    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["sagemaker.amazonaws.com"]
    }

    actions = [
      "sts:AssumeRole",
    ]
  }
}


data "aws_iam_policy_document" "notebook" {
  statement {
    sid = "AllowListTags"

    effect = "Allow"

    actions = [
      "sagemaker:ListTags",
    ]

    resources = ["arn:aws:sagemaker:*:*:notebook-instance/*"]
  }
}

resource "aws_iam_policy" "notebook" {
  name   = lower("${var.name_prefix}-sage-maker-repro")
  policy = data.aws_iam_policy_document.notebook.json
}

resource "aws_iam_role_policy_attachment" "notebook" {
  role       = aws_iam_role.notebook.name
  policy_arn = aws_iam_policy.notebook.arn
}

output "notebook_iam_role_arn" {
  value = aws_iam_role.notebook.arn
}

################################################################################
#  KMS Key
################################################################################

resource "aws_kms_key" "default" {
  description         = "${var.name_prefix}SageMakerRepro"
  enable_key_rotation = true
}

output "kms_key_id" {
  value = aws_kms_key.default.id
}

################################################################################
#  Lifecycle Config
################################################################################

resource "aws_sagemaker_notebook_instance_lifecycle_configuration" "default" {
  name     = "${var.name_prefix}SageMakerRepro"
  on_start = base64encode(<<-EOT
  #!/bin/bash
  echo "Hello from Notebook Lifecyce Config!"
  sleep 10
  echo "Goodbye from Notebook Lifecyce Config!"
  EOT
  )
}

output "lifecycle_config_name" {
  value = aws_sagemaker_notebook_instance_lifecycle_configuration.default.name
}
