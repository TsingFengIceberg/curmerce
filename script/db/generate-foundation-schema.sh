#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
source_sql="$repo_root/reference-submodules/ruoyi-vue-pro/sql/mysql/ruoyi-vue-pro.sql"
output_dir="$repo_root/target/generated-db"
output_sql="$output_dir/foundation-schema.sql"

expected_revision=ec3f7cbf73e88514a70a6b59d365092ee470603d
actual_revision="$(git -C "$repo_root/reference-submodules/ruoyi-vue-pro" rev-parse HEAD)"
if [[ "$actual_revision" != "$expected_revision" ]]; then
  printf 'Expected ruoyi-vue-pro %s, found %s\n' "$expected_revision" "$actual_revision" >&2
  exit 1
fi

tables=(
  infra_api_access_log infra_api_error_log infra_codegen_column infra_codegen_table
  infra_config infra_data_source_config infra_file infra_file_config infra_file_content
  infra_job infra_job_log system_dept system_dict_data system_dict_type system_login_log
  system_mail_account system_mail_log system_mail_template system_menu system_notice
  system_notify_message system_notify_template system_oauth2_access_token system_oauth2_approve
  system_oauth2_client system_oauth2_code system_oauth2_refresh_token system_operate_log
  system_post system_role system_role_menu system_sms_channel system_sms_code system_sms_log
  system_sms_template system_social_client system_social_user system_social_user_bind
  system_tenant system_tenant_package system_user_post system_user_role system_users
)

mkdir -p "$output_dir"
{
  printf '%s\n' '-- Generated from ruoyi-vue-pro ec3f7cbf73e88514a70a6b59d365092ee470603d.'
  printf '%s\n' '-- Table definitions only; all upstream records are intentionally excluded.'
  printf '%s\n' 'SET NAMES utf8mb4;'
  for table in "${tables[@]}"; do
    awk -v table="$table" '
      index($0, "CREATE TABLE `" table "`") == 1 { printing = 1 }
      printing { print }
      printing && /;$/ { found = 1; exit }
      END { if (!found) exit 2 }
    ' "$source_sql"
  done
} > "$output_sql"

printf '%s\n' "$output_sql"
