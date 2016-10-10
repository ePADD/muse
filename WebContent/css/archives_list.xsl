<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:template match="/archives">
		<html>
			<table cellpadding="0" cellspacing="0" border="0" id="table" class="tinytable">
				<thead class="tableheader">
					<tr>
						<th class="nosort"><h3>Collections</h3></th>
					</tr>
				</thead>

				<tbody>
					<xsl:for-each select="item">
						<xsl:variable name="number" select="number" />
						<xsl:variable name="file" select="file" />

						<tr>
							<td>
								<a href="#" onclick="javascript:muse.load_archive('{$number}', '#loadSessionSpinner{position()}', 'info?aId={$number}')">
									<xsl:value-of select="name" />
								</a>
								<img id="loadSessionSpinner{position()}" style="visibility:hidden" width="15" src="/muse/images/spinner.gif" />
								<svg class="legend"><g><path d="M0,10h20"></path></g></svg>
							</td>
						</tr>
					</xsl:for-each>
				</tbody>

			</table>
		</html>
	</xsl:template>
</xsl:stylesheet>