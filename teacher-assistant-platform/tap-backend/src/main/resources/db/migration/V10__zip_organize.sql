create table zip_organize_job (
  id bigint not null auto_increment,
  user_id bigint not null,
  status varchar(16) not null,
  original_filename varchar(512) not null,
  input_object_key text not null,
  output_object_key text null,
  report_object_key text null,
  provider varchar(32) not null default '',
  model varchar(64) not null default '',
  total_items int not null default 0,
  processed_items int not null default 0,
  success_items int not null default 0,
  failed_items int not null default 0,
  progress int not null default 0,
  retry_count int not null default 0,
  error_message text null,
  started_at timestamp(3) null,
  finished_at timestamp(3) null,
  created_at timestamp(3) not null default current_timestamp(3),
  updated_at timestamp(3) not null default current_timestamp(3),
  version bigint not null default 0,
  primary key (id),
  constraint fk_zip_organize_job_user foreign key (user_id) references tap_user(id) on delete cascade,
  constraint chk_zip_organize_job_status check (status in ('PENDING','RUNNING','SUCCEEDED','FAILED','CANCELLED'))
) engine=InnoDB;

create index idx_zip_organize_job_user_created on zip_organize_job(user_id, created_at desc);
create index idx_zip_organize_job_status_created on zip_organize_job(status, created_at);

create table zip_organize_item (
  id bigint not null auto_increment,
  job_id bigint not null,
  original_path text not null,
  filename varchar(512) not null,
  content_type varchar(128) not null,
  size_bytes bigint not null,
  sha256 varchar(64) not null,
  object_key text not null,
  extract_status varchar(16) not null,
  extracted_text_preview text null,
  extracted_text_key text null,
  doc_type varchar(32) null,
  paper_category varchar(64) null,
  paper_subtype varchar(64) null,
  title_guess varchar(512) null,
  author_guess varchar(256) null,
  year_guess int null,
  keywords_json json null,
  summary_zh text null,
  suggested_folder varchar(512) null,
  suggested_filename varchar(512) null,
  final_path text null,
  confidence double null,
  error_message text null,
  created_at timestamp(3) not null default current_timestamp(3),
  updated_at timestamp(3) not null default current_timestamp(3),
  primary key (id),
  constraint fk_zip_organize_item_job foreign key (job_id) references zip_organize_job(id) on delete cascade,
  constraint chk_zip_organize_item_extract_status check (extract_status in ('PENDING','EXTRACTED','EMPTY','FAILED'))
) engine=InnoDB;

create index idx_zip_organize_item_job_id on zip_organize_item(job_id);
create index idx_zip_organize_item_sha256 on zip_organize_item(sha256);
