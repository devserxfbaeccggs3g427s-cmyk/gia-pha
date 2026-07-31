terraform {
  required_version = ">= 1.9.0"
  required_providers {
    kafka = { source = "Mongey/kafka", version = "~> 0.7" }
  }
}

variable "cluster_name" { type = string }
variable "region"       { type = string }
variable "kafka_version" { type = string, default = "3.7" }
variable "broker_count" { type = number, default = 3 }

resource "kafka_topic" "events" {
  for_each = toset([
    "identity.events.v1",
    "tree.events.v1", "tree.memberships.v1",
    "member.events.v1", "relationship.events.v1", "event.events.v1",
    "media.events.v1", "sharing.events.v1", "search.events.v1",
    "transfer.events.v1", "operations.events.v1"
  ])

  name               = each.key
  partitions         = 12
  replication_factor = 3
  config = {
    "cleanup.policy"   = "delete"
    "retention.ms"     = "220752000000"   # 7 years for personal data
    "compression.type" = "zstd"
    "min.insync.replicas" = "2"
  }
}

resource "kafka_topic" "dlq" {
  for_each = toset([
    "identity.events.v1.dlq", "tree.events.v1.dlq", "member.events.v1.dlq",
    "relationship.events.v1.dlq", "event.events.v1.dlq", "media.events.v1.dlq",
    "sharing.events.v1.dlq", "search.events.v1.dlq", "transfer.events.v1.dlq",
    "operations.events.v1.dlq"
  ])

  name               = each.key
  partitions         = 6
  replication_factor = 3
  config = {
    "cleanup.policy"   = "delete"
    "retention.ms"     = "1209600000"   # 14 days
  }
}

output "bootstrap_servers" {
  value = "${var.cluster_name}.kafka.${var.region}.example.com:9092"
}
