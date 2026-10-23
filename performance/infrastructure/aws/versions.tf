terraform {
  required_version = ">= 1.13.0, < 2.0.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "6.69.1"
    }
  }
}

provider "aws" {
  region              = var.region
  allowed_account_ids = [var.account_id]
  default_tags {
    tags = {
      Project     = "konifer-performance"
      ManagedBy   = "opentofu"
      Environment = "aws-c9gd-v1"
      Stack       = var.stack_name
    }
  }
}
