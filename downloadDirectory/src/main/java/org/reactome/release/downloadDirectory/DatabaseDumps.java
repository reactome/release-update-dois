package org.reactome.release.downloadDirectory;

import org.gk.persistence.MySQLAdaptor;

public class DatabaseDumps {

	public static void execute(MySQLAdaptor dba) {
		// 1) Make databases directory
		// 2) Mysqldump test_reactome_66 > gk_current
		// 3) Mysqldump stable_identifiers > gk_stable_ids
		// 4) Mysqldump wordpress > gk_wordpress
		// 5) Mysqldump test_reactome_66_dn > gk_current_dn
		// 6) Create tmp_wordpress from gk_wordpress
		// 7) Sanitize wordpress using sanitize_wordpress.sql
		// 8) Mysqldump tmp_wordpress > gk_wordpress, drop tmp_wordpress
	}
}
