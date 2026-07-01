package com.colligendis.server.parser.numista.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class NumistaCollectionPageParserTest {

	private final NumistaCollectionPageParser parser = new NumistaCollectionPageParser(
			new NumistaCollectionSaveResponseParser());

	@Test
	void parsesVosPiecesTableRows() {
		String html = """
				<table id="vos_pieces">
				<tbody><tr><th colspan="16">Germany › German notgeld</th></tr>
				<tr id="t45564">
				    <td colspan="16" class="vos_pieces_type">
				        <a href="/45564">10 Pfennigs</a>
				        N# 45564
				    </td>
				</tr>
				<tbody class="par1">
				<tr class="date_row"><td colspan="16">1917</td></tr>
				</tbody>
				<tbody id="collec_line193184" class="collec par1">
				    <tr><td colspan="13">
				        <div style="display: flex;">
				            <span class="collec_q col45564">1&times;</span> AU
				            <span class="collec_price">RUB 1281.00</span>
				        </div>
				    </td><td colspan="3">
				        <button class="collec_edit" onclick="collec_modal_new('round', 45564, 193184, 58284093, 1, 'spl', 0, '1281.00', '', '', 0, [], null, null, '', '', null, '', '', null, '', '', null, null, null, ); return false;"></button>
				    </td></tr>
				</tbody>
				<tr id="t216431">
				    <td colspan="16" class="vos_pieces_type">
				        <a href="/216431">25 Pfennigs</a>
				    </td>
				</tr>
				<tbody id="collec_line536093" class="collec par1">
				    <tr><td colspan="13">
				        <div style="display: flex; flex-wrap: wrap;">
				            <span class="collec_q col216431">3&times;</span> XF
				            <span class="collec_pictures" data-thumb-id="78514703" data-thumb-user="243029"></span>
				            <span class="collec_comment">Some private comment</span>
				        </div>
				    </td><td colspan="3">
				        <button class="collec_edit" onclick="collec_modal_new('paper', 216431, 536093, 78514703, 3, 'sup', 0, '1020.00', 'Some private comment', '', 0, [], '1', '185', '{&quot;gradingDesignation&quot;:[&quot;359&quot;]}', '', '', 'Ushkov', '', '', '', '', '', null, null, null, ); return false;"></button>
				    </td></tr>
				</tbody>
				</table>
				""";

		var items = parser.parse(html);
		assertEquals(2, items.size());

		NumistaCollectionSaveResponse first = items.get(0);
		assertEquals("58284093", first.getNumistaCollectionItemId());
		assertEquals("45564", first.getCoinId());
		assertEquals("193184", first.getVersionId());
		assertEquals(1, first.getQuantity());

		NumistaCollectionSaveResponse second = items.get(1);
		assertNotNull(second);
		assertEquals("78514703", second.getNumistaCollectionItemId());
		assertEquals("216431", second.getCoinId());
		assertEquals("536093", second.getVersionId());
		assertEquals(3, second.getQuantity());
		assertEquals("Some private comment", second.getComment());
	}
}
