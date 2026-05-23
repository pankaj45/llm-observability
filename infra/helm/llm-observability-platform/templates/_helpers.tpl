{{- define "llm-observability.name" -}}
llm-observability-platform
{{- end -}}

{{- define "llm-observability.labels" -}}
app.kubernetes.io/name: {{ include "llm-observability.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "llm-observability.secretName" -}}
{{- if .Values.secrets.create -}}
{{ include "llm-observability.name" . }}-secrets
{{- else -}}
{{ .Values.secrets.existingSecret }}
{{- end -}}
{{- end -}}
