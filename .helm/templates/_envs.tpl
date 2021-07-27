{{- define "common_envs" }}
- name: JAVA_OPTS
  value: {{ pluck .Values.global.env .Values.java_opts | first | default .Values.java_opts._default }}
{{- end }}
