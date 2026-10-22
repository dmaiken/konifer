output "connections" {
  value = {
    environment      = "aws-c9gd-v1"
    region           = var.region
    availabilityZone = var.availability_zone
    koniferPrivateIp = aws_instance.host["konifer"].private_ip
    k6PrivateIp      = aws_instance.host["k6"].private_ip
    rdsEndpoint      = aws_db_instance.benchmark.address
    bucketName       = aws_s3_bucket.benchmark["objects"].id
  }
}

output "deployment" {
  value = {
    accountId        = var.account_id
    region           = var.region
    stackName        = var.stack_name
    availabilityZone = var.availability_zone
    amiId            = var.ami_id
    vpcId            = aws_vpc.benchmark.id
    subnetId         = aws_subnet.hosts.id
    s3EndpointId     = aws_vpc_endpoint.s3.id
    routeTableId     = aws_route_table.hosts.id
    instances        = { for name, host in aws_instance.host : name => host.id }
    securityGroups   = merge({ for name, sg in aws_security_group.host : name => sg.id }, { database = aws_security_group.database.id })
    databaseId       = aws_db_instance.benchmark.identifier
    postgresVersion  = aws_db_instance.benchmark.engine_version_actual
    secretArn        = aws_db_instance.benchmark.master_user_secret[0].secret_arn
    stagingBucket    = aws_s3_bucket.benchmark["staging"].id
    objectBucket     = aws_s3_bucket.benchmark["objects"].id
  }
}
