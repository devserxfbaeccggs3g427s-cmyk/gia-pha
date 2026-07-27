{{- include "familya.tpl" . }}
{{- /* service identity */ -}}
{{- $svc := .Values.name -}}
{{- if not $svc -}}
{{- fail "values.name is required" -}}
{{- end -}}
