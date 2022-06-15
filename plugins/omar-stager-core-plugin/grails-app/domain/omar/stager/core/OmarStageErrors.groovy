package omar.stager.core


class OmarStageErrors {
    String filename
    String statusMessage
    Date dateCreated

    static constraints = {
        filename nullable:false
        statusMessage nullable:true
        dateCreated nullable: true
    }

    static mapping = {
        cache true
        id generator: 'identity'
        filename type: 'text', index: 'omar_stage_error_filename_idx'
        statusMessage type: 'text'
        dateCreated index: 'omar_stage_error_date_created_idx'
    }

    def beforeInsert() {
        if ( dateCreated == null ) dateCreated = new Date()
        true
    }
}
