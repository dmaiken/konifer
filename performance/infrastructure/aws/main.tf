data "aws_partition" "current" {}

resource "aws_vpc" "benchmark" {
  cidr_block           = "10.73.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = var.stack_name }
}

resource "aws_internet_gateway" "benchmark" {
  vpc_id = aws_vpc.benchmark.id
}

resource "aws_subnet" "hosts" {
  vpc_id                  = aws_vpc.benchmark.id
  cidr_block              = "10.73.1.0/24"
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = true
}

resource "aws_subnet" "database" {
  for_each = {
    primary   = { cidr = "10.73.2.0/24", az = var.availability_zone }
    secondary = { cidr = "10.73.3.0/24", az = var.secondary_availability_zone }
  }
  vpc_id            = aws_vpc.benchmark.id
  cidr_block        = each.value.cidr
  availability_zone = each.value.az
}

resource "aws_route_table" "hosts" {
  vpc_id = aws_vpc.benchmark.id
}

resource "aws_route" "internet" {
  route_table_id         = aws_route_table.hosts.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.benchmark.id
}

resource "aws_route_table_association" "hosts" {
  subnet_id      = aws_subnet.hosts.id
  route_table_id = aws_route_table.hosts.id
}

resource "aws_route_table" "database" {
  vpc_id = aws_vpc.benchmark.id
}

resource "aws_route_table_association" "database" {
  for_each       = aws_subnet.database
  subnet_id      = each.value.id
  route_table_id = aws_route_table.database.id
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.benchmark.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.hosts.id]
}

resource "aws_security_group" "host" {
  for_each    = toset(["konifer", "k6"])
  name_prefix = "${var.stack_name}-${each.key}-"
  vpc_id      = aws_vpc.benchmark.id
  description = "Benchmark ${each.key}; no public ingress"
}

resource "aws_vpc_security_group_egress_rule" "hosts" {
  for_each          = aws_security_group.host
  security_group_id = each.value.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
  description       = "Bootstrap, AWS services and private benchmark traffic"
}

resource "aws_vpc_security_group_ingress_rule" "application" {
  security_group_id            = aws_security_group.host["konifer"].id
  referenced_security_group_id = aws_security_group.host["k6"].id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

resource "aws_security_group" "database" {
  name_prefix = "${var.stack_name}-database-"
  vpc_id      = aws_vpc.benchmark.id
  description = "PostgreSQL from Konifer only"
}

resource "aws_vpc_security_group_ingress_rule" "database" {
  security_group_id            = aws_security_group.database.id
  referenced_security_group_id = aws_security_group.host["konifer"].id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

resource "aws_s3_bucket" "benchmark" {
  for_each      = toset(["objects", "staging"])
  bucket        = "${var.stack_name}-${var.account_id}-${var.region}-${each.key}"
  force_destroy = true
}

resource "aws_s3_bucket_ownership_controls" "benchmark" {
  for_each = toset(["objects", "staging"])
  bucket   = aws_s3_bucket.benchmark[each.key].id
  rule { object_ownership = "BucketOwnerEnforced" }
}

resource "aws_s3_bucket_public_access_block" "benchmark" {
  for_each                = toset(["objects", "staging"])
  bucket                  = aws_s3_bucket.benchmark[each.key].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "benchmark" {
  for_each = toset(["objects", "staging"])
  bucket   = aws_s3_bucket.benchmark[each.key].id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}

resource "aws_s3_bucket_policy" "https" {
  for_each = toset(["objects", "staging"])
  bucket   = aws_s3_bucket.benchmark[each.key].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "RequireTLS"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.benchmark[each.key].arn, "${aws_s3_bucket.benchmark[each.key].arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })
}

resource "aws_s3_bucket_lifecycle_configuration" "benchmark" {
  for_each = toset(["objects", "staging"])
  bucket   = aws_s3_bucket.benchmark[each.key].id
  rule {
    id     = "abort-incomplete-uploads"
    status = "Enabled"
    filter { prefix = "" }
    abort_incomplete_multipart_upload { days_after_initiation = 1 }
  }
}

resource "aws_db_subnet_group" "benchmark" {
  name       = var.stack_name
  subnet_ids = [for subnet in aws_subnet.database : subnet.id]
}

resource "aws_db_parameter_group" "benchmark" {
  name_prefix = "${var.stack_name}-"
  family      = "postgres17"
  description = "Isolated historical benchmarks; older R2DBC drivers hardcode TLS off"
  parameter {
    name         = "rds.force_ssl"
    value        = "0"
    apply_method = "pending-reboot"
  }
}

resource "aws_db_instance" "benchmark" {
  identifier                   = var.stack_name
  engine                       = "postgres"
  engine_version               = var.postgres_version
  instance_class               = "db.m7g.large"
  allocated_storage            = 20
  storage_type                 = "gp3"
  storage_encrypted            = true
  db_name                      = "konifer"
  username                     = "konifer_user"
  manage_master_user_password  = true
  availability_zone            = var.availability_zone
  db_subnet_group_name         = aws_db_subnet_group.benchmark.name
  vpc_security_group_ids       = [aws_security_group.database.id]
  parameter_group_name         = aws_db_parameter_group.benchmark.name
  multi_az                     = false
  publicly_accessible          = false
  auto_minor_version_upgrade   = false
  backup_retention_period      = 0
  skip_final_snapshot          = true
  delete_automated_backups     = true
  deletion_protection          = false
  performance_insights_enabled = false
}

resource "aws_iam_role" "host" {
  for_each    = toset(["konifer", "k6"])
  name_prefix = "${var.stack_name}-${each.key}-"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow", Action = "sts:AssumeRole", Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  for_each   = toset(["konifer", "k6"])
  role       = aws_iam_role.host[each.key].name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "staging" {
  for_each = toset(["konifer", "k6"])
  name     = "read-bootstrap"
  role     = aws_iam_role.host[each.key].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject"]
      Resource = ["${aws_s3_bucket.benchmark["staging"].arn}/shared/*", "${aws_s3_bucket.benchmark["staging"].arn}/${each.key}/*"]
    }]
  })
}

resource "aws_iam_role_policy" "results" {
  name = "write-results"
  role = aws_iam_role.host["k6"].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:PutObject", "s3:AbortMultipartUpload"]
      Resource = ["${aws_s3_bucket.benchmark["staging"].arn}/k6/results/*"]
    }]
  })
}

resource "aws_iam_role_policy" "konifer" {
  name = "application"
  role = aws_iam_role.host["konifer"].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["s3:ListBucket", "s3:ListBucketMultipartUploads"], Resource = [aws_s3_bucket.benchmark["objects"].arn] },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:AbortMultipartUpload", "s3:ListMultipartUploadParts"]
        Resource = ["${aws_s3_bucket.benchmark["objects"].arn}/*"]
      },
      {
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = [aws_db_instance.benchmark.master_user_secret[0].secret_arn]
      }
    ]
  })
}

resource "aws_iam_instance_profile" "host" {
  for_each    = toset(["konifer", "k6"])
  name_prefix = "${var.stack_name}-${each.key}-"
  role        = aws_iam_role.host[each.key].name
}

resource "aws_instance" "host" {
  for_each = { konifer = "c9gd.xlarge", k6 = "c9g.medium" }

  ami                         = var.ami_id
  instance_type               = each.value
  subnet_id                   = aws_subnet.hosts.id
  vpc_security_group_ids      = [aws_security_group.host[each.key].id]
  associate_public_ip_address = true
  iam_instance_profile        = aws_iam_instance_profile.host[each.key].name
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }
  root_block_device {
    encrypted             = true
    volume_size           = 30
    volume_type           = "gp3"
    delete_on_termination = true
  }
  user_data                   = <<-USERDATA
    #!/bin/bash
    set -euo pipefail
    systemctl enable --now snap.amazon-ssm-agent.amazon-ssm-agent.service
  USERDATA
  user_data_replace_on_change = true
  tags                        = { Name = "${var.stack_name}-${each.key}", Role = each.key }

  depends_on = [aws_route.internet, aws_route_table_association.hosts, aws_iam_role_policy_attachment.ssm]
}
