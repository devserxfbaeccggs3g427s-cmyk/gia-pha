terraform {
  required_version = ">= 1.9.0"
  required_providers {
    mysql = { source = "terraform-providers/mysql", version = "~> 1.10" }
  }
}

variable "service_name"        { type = string }
variable "mysql_version"       { type = string, default = "8.4" }
variable "instance_class"      { type = string, default = "db.r6g.large" }
variable "multi_az"            { type = bool,   default = true }
variable "backup_retention"    { type = number, default = 14 }
variable "kms_key_id"          { type = string }

resource "aws_db_instance" "service" {
  identifier              = "familya-${var.service_name}"
  engine                  = "mysql"
  engine_version          = var.mysql_version
  instance_class          = var.instance_class
  allocated_storage       = 100
  max_allocated_storage   = 1000
  storage_encrypted       = true
  kms_key_id              = var.kms_key_id
  multi_az                = var.multi_az
  backup_retention_period = var.backup_retention
  enabled_cloudwatch_logs_exports = ["audit", "error", "general", "slowquery"]
  deletion_protection     = true
  publicly_accessible     = false
  username                = "admin"
  password                = data.aws_secretsmanager_secret_version.db_password.secret_string
  vpc_security_group_ids  = [var.db_security_group_id]
  db_subnet_group_name    = var.db_subnet_group_name
  parameter_group_name    = aws_db_parameter_group.service.name
  skip_final_snapshot     = false
  final_snapshot_identifier = "familya-${var.service_name}-final"
  enabled_log_types       = []
  tags = {
    Service = var.service_name
    Owner   = "platform"
    PII     = "true"
  }
}

resource "aws_db_parameter_group" "service" {
  name   = "familya-${var.service_name}"
  family = "mysql8.4"
  parameter {
    name  = "binlog_format"
    value = "ROW"
  }
  parameter {
    name  = "innodb_flush_log_at_trx_commit"
    value = "1"
  }
}

data "aws_secretsmanager_secret_version" "db_password" {
  secret_id = "familya/${var.service_name}/db"
}

output "endpoint" { value = aws_db_instance.service.endpoint }
output "port"    { value = aws_db_instance.service.port }
