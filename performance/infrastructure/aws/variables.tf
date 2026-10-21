variable "account_id" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "A prepared AWS account ID is required."
  }
}

variable "region" {
  type    = string
  default = "us-east-2"
}

variable "availability_zone" {
  type    = string
  default = "us-east-2a"
}

variable "secondary_availability_zone" {
  type    = string
  default = "us-east-2b"
}

variable "ami_id" {
  type = string
  validation {
    condition     = can(regex("^ami-[a-f0-9]+$", var.ami_id))
    error_message = "prepare must resolve an Ubuntu 24.04 ARM64 AMI."
  }
}

variable "stack_name" {
  type    = string
  default = "konifer-perf"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,23}$", var.stack_name))
    error_message = "Stack name must be 3–24 lowercase letters, digits or hyphens, starting with a letter."
  }
}

variable "postgres_version" {
  type    = string
  default = "17.11"
  validation {
    condition     = can(regex("^17\\.[0-9]+$", var.postgres_version))
    error_message = "This environment requires a pinned PostgreSQL 17 minor version."
  }
}
