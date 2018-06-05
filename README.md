# mir-alias-module
Every MIR document is addressed to a mods identifier, e.g. http://www.mycore.de/mir/receive/mir_mods_00000003.
This plugin extends this url with an alias mechanismus. The mir-admin document form is expanded with alias fields.

With a given alias mechanismus the previous URL is also reachable via created alias-URL, for example "http://localhost:8291/mir/go/oa/publicationsfonds"

## Installation instructions (As mir-enduser)

1. Download the project from this repository and place it on your computer

2. Unzip the downloaded file to create a development project folder location 

3. Customize general mycore.properties(http://www.mycore.de/documentation/getting_started/mcr_properties.html) for this plugin (/mir-alias-plugin/src/main/resources/config/mir-alias-plugin/mycore.properties)

4. Create jar file with maven => mvn clean && mvn install

5. Copy the created jar file from target (/mir-alias-plugin/target) to mycore home lib 

(Windows Systems C:\Users\User\AppData\Local\MyCoRe\mirapplication\lib) <br />
(Linux Systems /home/user/.mycore/mirapplication/lib)

6. Readjust solr schema with new alias parameter (http://www.mycore.de/documentation/getting_started/solr_7.html)

Add the following lines to schema.xml 

		<!-- Alias (/go/* URLs) -->
		<field name="alias" type="string" indexed="true" stored="true" multiValued="false"/>


