<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
  xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:xalan="http://xml.apache.org/xalan"
  xmlns:mods="http://www.loc.gov/mods/v3" 
  exclude-result-prefixes="xalan xlink mods">
  <xsl:import href="xslImport:solr-document:solr-alias.xsl" />

  <xsl:template match="mycoreobject">
    <xsl:apply-imports />
    <xsl:apply-templates select="service/servflags/servflag[@type='alias']" mode="alias" />
  </xsl:template>
  
  <xsl:template match="service/servflags/servflag[@type='alias']" mode="alias">
    <field name="alias">
      <xsl:value-of select="text()" />
    </field>
  </xsl:template>
</xsl:stylesheet>