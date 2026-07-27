terraform {
  required_version = ">= 1.9.0"
  backend "s3" {
    # configuration supplied per-environment via -backend-config
  }
}

provider "aws" {
  region = var.region
}

module "kubernetes" {
  source      = "../../modules/managed-kubernetes"
  cluster_name = "familya-${var.environment}"
  region       = var.region
}

module "kms_identity" {
  source       = "../../modules/managed-kms"
  service_name = "identity"
  environment  = var.environment
}

module "mysql_identity" {
  source               = "../../modules/managed-mysql"
  service_name         = "identity"
  kms_key_id           = module.kms_identity.kms_key_arn
  db_subnet_group_name = "familya-${var.environment}-db"
  db_security_group_id = "sg-placeholder"
}

module "kafka" {
  source       = "../../modules/managed-kafka"
  cluster_name = "familya-${var.environment}"
  region       = var.region
}

module "observability" {
  source      = "../../modules/managed-observability"
  environment = var.environment
}

variable "environment" { type = string, default = "example" }
variable "region"      { type = string, default = "ap-southeast-1" }

output "namespace_map" { value = module.kubernetes.namespace_map }
output "identity_db"   { value = module.mysql_identity.endpoint }
output "kafka"         { value = module.kafka.bootstrap_servers }
