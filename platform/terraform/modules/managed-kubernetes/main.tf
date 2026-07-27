terraform {
  required_version = ">= 1.9.0"
  required_providers {
    kubernetes = { source = "hashicorp/kubernetes", version = "~> 2.31" }
    helm       = { source = "hashicorp/helm",       version = "~> 2.13" }
  }
}

variable "cluster_name" { type = string }
variable "region"       { type = string }
variable "service_names" {
  type    = list(string)
  default = [
    "identity", "tree-access", "member", "relationship", "event",
    "media", "sharing", "search", "transfer", "audit-ops", "migration"
  ]
}

resource "kubernetes_namespace" "service" {
  for_each = toset(var.service_names)
  metadata {
    name        = "familya-${each.key}"
    annotations = { "pod-security.kubernetes.io/enforce" = "restricted" }
  }
}

resource "kubernetes_service_account" "workload" {
  for_each = toset(var.service_names)
  metadata {
    name      = "${each.key}-workload"
    namespace = kubernetes_namespace.service[each.key].metadata[0].name
    annotations = {
      "iam.amazonaws.com/role" = "arn:aws:iam::000000000000:role/${each.key}-workload"
    }
  }
}

resource "kubernetes_network_policy" "default_deny" {
  for_each = toset(var.service_names)
  metadata {
    name      = "default-deny"
    namespace = kubernetes_namespace.service[each.key].metadata[0].name
  }
  spec {
    pod_selector {}
    policy_types = ["Ingress", "Egress"]
  }
}

resource "kubernetes_network_policy" "allow_gateway" {
  for_each = toset(var.service_names)
  metadata {
    name      = "allow-gateway"
    namespace = kubernetes_namespace.service[each.key].metadata[0].name
  }
  spec {
    pod_selector {}
    ingress {
      from {
        namespace_selector { match_labels = { name = "familya-gateway" } }
      }
    }
    policy_types = ["Ingress"]
  }
}

resource "kubernetes_resource_quota" "service" {
  for_each = toset(var.service_names)
  metadata {
    name      = "quota"
    namespace = kubernetes_namespace.service[each.key].metadata[0].name
  }
  spec {
    hard = {
      pods                   = 50
      "requests.cpu"         = "10"
      "requests.memory"      = "20Gi"
      "limits.cpu"           = "20"
      "limits.memory"        = "40Gi"
      "persistentvolumeclaims" = 5
    }
  }
}

output "namespace_map" {
  value = { for k, ns in kubernetes_namespace.service : k => ns.metadata[0].name }
}
