{{- define "app.tag" -}}
{{- if eq .Chart.AppVersion "latest" }}
{{- "latest" }}
{{- else }}
{{- printf "v%s" .Chart.AppVersion }}
{{- end }}
{{- end }}
