terraform {
  required_version = ">= 1.9.0"
  required_providers {
    grafana = { source = "grafana/grafana", version = "~> 3.0" }
  }
}

variable "environment" { type = string }

resource "grafana_folder" "platform" {
  title = "familya/${var.environment}"
}

resource "grafana_dashboard" "slo" {
  config_json = jsonencode({
    title = "Family Tree SLOs"
    uid   = "familya-slos"
    panels = [
      { type = "timeseries", title = "Mutation acceptance p95" },
      { type = "timeseries", title = "Saga completion p95" },
      { type : "timeseries", title : "Projection lag p95" },
      { type = "timeseries", title = "Outbox age p99" },
      { type = "timeseries", title = "Consumer lag" }
    ]
  })
  folder      = grafana_folder.platform.id
}
