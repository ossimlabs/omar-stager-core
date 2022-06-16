package omar.stager.core

import omar.core.Repository
import omar.core.HttpStatus
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.MessageSource
import grails.core.GrailsApplication
import java.text.SimpleDateFormat
import org.springframework.beans.factory.annotation.Value
import io.micronaut.http.client.HttpClient
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpRequest


class IngestService implements ApplicationContextAware
{
	static transactional = true

	GrailsApplication grailsApplication

	def applicationContext

	MessageSource messageSource

	@Value('${stager.errors.table.enabled:true}')
    Boolean errorsToTable
    @Value('${stager.errors.slack.enabled:false}')
    Boolean errorsToSlack
	@Value('${stager.errors.slack.webhook}')
	String slackWebhook
	@Value('${stager.errors.slack.messagePrefix}')
	String slackPrefix

	def ingest( def oms, def baseDir = '/' )
	{
		def status  = HttpStatus.OK
		def message = ""

		def errorFileEnabled = grailsApplication.config.getProperty('stager.errorFile.enabled', Boolean, false)

		if ( oms )
		{
			def omsInfoParsers = applicationContext.getBeansOfType( OmsInfoParser.class )
			def repository = Repository.findByBaseDir( ( baseDir as File ).absolutePath )

			for ( def parser in omsInfoParsers?.values() )
			{
				def dataSets = parser.processDataSets( oms, repository )

				for ( def dataSet in dataSets )
				{

					if ( dataSet.save() )
					{
						status = HttpStatus.OK
						message = "Added dataset"
					}
					else
					{
						status = HttpStatus.UNSUPPORTED_MEDIA_TYPE

						message = dataSet.errors.allErrors.collect{ e ->
							messageSource.getMessage(e, Locale.default)
						}.join(' ')

						def filename = dataSet.fileObjects.find { it.type == 'main' }.name

						log.error("🚩 Error: ${filename} ${status} ${message}")
						ingestService.writeErrors(filename, message, status)

					}
				}
			}
		}

		return [ status, message ]
	}

	synchronized def findRepositoryForFile( def file )
	{
		def repository

		if ( File.separatorChar == '\\' )
		{
			// I am having troubles with windows.  We will address this when we refactor the
			// repo implementation
			//
			repository = Repository.findByBaseDir( "/" );

			if ( !repository )
			{
				repository = new Repository( baseDir: "/" )
				repository.save( flush: true )
				log.debug( "Creating default repository /" )
			}

			if( !repository )
			{
				log.error ("Could not create repository")
			}

			return repository
		}
		else
		{
			// log.error("IngestService: Does not contain the proper separatorChar")
			log.debug("You may not have the appropriate permissions")
		}
	}

	/**
	 * Creates a text file with the image file name and path,
	 * and the reason the stage/ingest was not successful.  This file
	 * can be used troubleshoot, and help to restage the image at a later time.
	 *
	 * @param filename The image file name
	 * @param message Http status message
	 * @param status Http status code
	 */
	def createErrorFile (String filename, String message, Integer status) {
		def errorFileDir = grailsApplication.config.getProperty('stager.errorFile.directory', String, "/tmp/omar-stager/errors/")

		String pattern = "yyyy-MM-dd-HH-mm-ss-"
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern)
		String datePrefix = simpleDateFormat.format(new Date())

		File errorDir = errorFileDir as File
		errorDir.mkdirs()

		File errorFileName = File.createTempFile("stager-${datePrefix}", ".txt", errorDir)

		errorFileName.withWriter { out ->
			out.println "filename: ${filename}"
			out.println "message: ${message}"
			out.println "status: ${status}"

		}
	}

	/**
	 * If enabled, sends a slack message to the webhook that is configured
	 * in the omar-stager configmap.
	 * If enabled, writes the error to the omar_stager_errors table.
	 *
	 * @param filename The image file name
	 * @param message Http status message
	 * @param status Http status code
	 */
	def writeErrors (String filename, String message, Integer status) {

        if (errorsToSlack) {
			log.info("Writing error to slack.")
			URL slack = new URL(slackWebhook)
			HttpClient httpClient = HttpClient.create(new URL(slack.toString() - slack.path))

			// Pull in the message from the configmap and replace the placeholders
			String slackMessage = "${slackPrefix}\nFile: ```${ -> filename}```\nError: ```${-> message}```"

			Map<String, String> slackMessageTemplate = new HashMap<>()
  				slackMessageTemplate.put("text", slackMessage)
				slackMessageTemplate.put("type","mrkdwn")
			MutableHttpRequest<String> request = HttpRequest.POST(slack.path, "");
			request.header("Content-Type", "application/json");
			request.body(slackMessageTemplate)
			HttpResponse<String> response = httpClient.toBlocking().exchange(request, String)

		}
        if (errorsToTable) {
			log.info("Writing error to table.")
            def logErrors = new OmarStagerErrors(
                                            filename: filename,
                                            statusMessage: message,
											status: status
                                            )
            if ( !logErrors.save() ) {
                	logErrors.errors.allErrors.each {
                    println messageSource.getMessage( it, Locale.default )
                }
            }
        }
    }

	void setApplicationContext( ApplicationContext applicationContext )
	{
		this.applicationContext = applicationContext
	}
}
