{{- define "common_envs" }}
- name: JAVA_OPTS
  value: {{ pluck .Values.werf.env .Values.java_opts | first | default .Values.java_opts._default }}
- name: GIT_SSH_COMMAND
  value: {{ pluck .Values.werf.env .Values.git_ssh_command | first | default .Values.git_ssh_command._default }}
- name: TZ
  value: Europe/Moscow
{{- end }}
