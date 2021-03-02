<?xml version="1.0" encoding="UTF-8"?>
<!-- Stylesheet to turn the XML output of CPD into a nice-looking HTML page -->
<!-- Source: https://github.com/pmd/pmd/blob/master/pmd-core/etc/xslt/cpdhtml.xslt -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xpath-default-namespace="http://pmd.sourceforge.net/report/2.0.0" version="2.0">
    <xsl:output method="html" encoding="UTF-8" doctype-public="-//W3C//DTD HTML 4.01 Transitional//EN"
                doctype-system="http://www.w3.org/TR/html4/loose.dtd" indent="yes"/>

    <xsl:template match="pmd-cpd">
        <html>
            <head>
                <script type="text/javascript">
                    function toggleCodeSection(btn, id)
                    {
                    area = document.getElementById(id);
                    if (area.style.display == 'none')
                    {
                    btn.innerHTML = '-';
                    area.style.display = 'inline';
                    }
                    else
                    {
                    btn.innerHTML = '+';
                    area.style.display = 'none';
                    }
                    }
                </script>
                <style>
                    .SummaryTitle  { }
                    .SummaryNumber { background-color:#DDDDDD; text-align: center; }
                    .ItemNumber    { background-color: #DDDDDD; }
                    .CodeFragment  { background-color: #BBBBBB; display:none; font:normal normal normal 9pt Courier; }
                    .ExpandButton  { background-color: #FFFFFF; font-size: 8pt; width: 20px; height: 20px; margin:0px; }
                </style>
            </head>
            <body>
                <h2>Summary of duplicated code</h2>
                This page summarizes the code fragments that have been found to be replicated in the code.
                <p/>
                <table border="1" class="summary" cellpadding="2">
                    <tr style="background-color:#CCCCCC;">
                        <th># duplications</th>
                        <th>Total lines</th>
                        <th>Total tokens</th>
                        <th>Approx # bytes</th>
                    </tr>
                    <tr>
                        <td class="SummaryNumber"><xsl:value-of select="count(//duplication)"/></td>
                        <td class="SummaryNumber"><xsl:value-of select="sum(//duplication/@lines)"/></td>
                        <td class="SummaryNumber"><xsl:value-of select="sum(//duplication/@tokens)"/></td>
                        <td class="SummaryNumber"><xsl:value-of select="sum(//duplication/@tokens) * 4"/></td>
                    </tr>
                </table>
                <p/>
                You expand and collapse the code fragments using the + buttons. You can also navigate to the source code by clicking
                on the file names.
                <p/>
                <table>
                    <tr style="background-color: #444444; color: #DDDDDD;"><td>ID</td><td>Files</td><td>Lines</td></tr>
                    <xsl:for-each select="//duplication">
                        <xsl:sort data-type="number" order="descending" select="@lines"/>
                        <tr>
                            <td class="ItemNumber"><xsl:value-of select="position()"/></td>
                            <td>
                                <table>
                                    <xsl:for-each select="file">
                                        <tr><td><a><xsl:attribute name="href">../src/<xsl:value-of select="@path"/>.html#<xsl:value-of select="@line"/></xsl:attribute><xsl:value-of select="@path"/></a></td><td> line <xsl:value-of select="@line"/></td></tr>
                                    </xsl:for-each>
                                </table>
                            </td>
                            <td># lines : <xsl:value-of select="@lines"/></td>
                        </tr>
                        <tr>
                            <td> </td>
                            <td colspan="2" valign="top">
                                <table><tr>
                                    <td valign="top">
                                        <button class="ExpandButton" ><xsl:attribute name="onclick">blur(); toggleCodeSection(this, 'frag_<xsl:value-of select="position()"/>')</xsl:attribute>+</button>
                                    </td>
                                    <td>
                                        <textarea cols="100" rows="30" wrap="off" class='CodeFragment' style='display:none;'>
                                            <xsl:attribute name="id">frag_<xsl:value-of select="position()"/></xsl:attribute>
                                            <xsl:value-of select="codefragment"/>
                                        </textarea>
                                    </td>
                                </tr></table>
                            </td>
                        </tr>
                        <tr><td colspan="2"><hr/></td></tr>
                    </xsl:for-each>
                </table>


            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>