package omar.stager.core

import java.sql.Timestamp

class OmarStagerErrors {
    String filename
    String statusMessage
    Timestamp errorDate
    String status

    static constraints = {
        filename ( nullable: true )
        statusMessage ( nullable: true )
        status (nullable: true )
        errorDate ( nullable: true )
    }

    static mapping = {
        cache true
        id generator: 'identity'
        filename type: 'text', index: 'omar_stager_errors_filename_idx'
        statusMessage type: 'text'
        status type: 'text'
        errorDate index: 'omar_stager_errors_date_idx', sqlType: "timestamp with time zone" /*, type: PersistentDateTime*/
    }

    def beforeInsert() {
        if ( !errorDate )
            {
                errorDate = Calendar.getInstance(TimeZone.getTimeZone('GMT')).time.toTimestamp()
            }

        true
    }
}
