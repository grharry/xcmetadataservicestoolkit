create table repo_name.record_links (
  from_record_id   bigint       not null,
  to_record_id     bigint       not null,
  primary key(from_record_id, to_record_id),
  KEY idx_links_from_record_id(from_record_id),
  KEY idx_links_to_record_id(to_record_id)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

create table repo_name.prev_incoming_record_statuses (
  record_id        bigint       not null,
  status           char(1)      not null,

  primary key (record_id)
)
