# mir-alias-module
Every MIR document is addressed to a mods identifier, e.g. http://www.mycore.de/mir/receive/mir_mods_00000003.
This plugin extends this url with an alias mechanismus. The mir-admin document form is expanded with alias fields.

With a given alias mechanismus the previous URL is also reachable via created alias-URL, for example "http://.../mir/go/oa/publicationsfonds"

## Features
### Alias basic resolvement
Alias allocation is affiliated to mir editor-admins.xed.
After save the document alias is available via: 

**{webApplicationBaseURL}/{aliasConfParameter}/{alias-part}**.

For the shown case alias is available through **http://localhost:8291/go/sozial.geschichte-online**


### Related Items with Alias Structure
The Alias plugin recognizes alias structure in related items.You can allocate this 
way an expanded alias structure. The URL will be generated automatically based on the information
from your alias structure.

### Multiple Aliases
The alias structure is based on a tree. It is possible to assign multiple alises.



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


