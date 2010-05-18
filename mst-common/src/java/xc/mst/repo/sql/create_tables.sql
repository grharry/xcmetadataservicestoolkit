create table repo_name_records (
	record_id        int        NOT NULL    AUTO_INCREMENT,
	oai_pmh_id_1     char(35),
	oai_pmh_id_2     char(35),
	oai_pmh_id_3     char(35),
	oai_pmh_id_4     char(35),
	date_created     datetime,
	status           char(1),
	format_id        int,

	PRIMARY KEY (record_id),
	KEY idx_REPO_NAME_records_oai_pmh_id_4 (oai_pmh_id_1),
	KEY idx_REPO_NAME_records_date_created (date_created),
	KEY idx_REPO_NAME_records_status (status),
	KEY idx_REPO_NAME_records_format_id (format_id)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

create table repo_name_record_updates (
	id                    int        NOT NULL    AUTO_INCREMENT,
	record_id             int,
	date_updated          datetime,
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

create table repo_name_records_xml (
	record_id      int         primary key,
	xml            longtext
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

create table repo_name_record_set (
	record_id             int,
	set_id                int,
	PRIMARY KEY (record_id, set_id),
	KEY idx_REPO_NAME_records_set_record_id(record_id),
	KEY idx_REPO_NAME_records_set_set_id(set_id)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

create table repo_name_record_predecessors (
	id                    int        NOT NULL    AUTO_INCREMENT,
	record_id             int,
	success_repo_name     char(20),
	successor_record_id   int,
	PRIMARY KEY (id),
	KEY idx_REPO_NAME_records_set_record_id(record_id)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;