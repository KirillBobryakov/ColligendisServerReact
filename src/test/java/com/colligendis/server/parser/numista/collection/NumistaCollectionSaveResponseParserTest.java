package com.colligendis.server.parser.numista.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NumistaCollectionSaveResponseParserTest {

	private final NumistaCollectionSaveResponseParser parser = new NumistaCollectionSaveResponseParser();

	@Test
	void parsesSampleCollectionRow() {
		String html = """
				<tr>
				    <td colspan="13">
				        <div style="display: flex; flex-wrap: wrap; column-gap: 0.5em; row-gap: 0; align-items: center;">
				            <span class="collec_q col216431">3&times;</span>
				             XF
				            <span>
				                <span class="collec_pictures thumbnail_opener" tabindex="0" data-thumb-id="78514703" data-thumb-user="243029" data-thumb-pictures="01ce3c701d804805715f837712a60190fab29146768b235fc0aaf9d7d84844a2.png bb387e8205c6047025d1d7d9776e24aafc5afe22db9e8f359f7c18e6d5d0b6af.png"></span>
				            </span>
				            <span class="collec_slab">NGC XF ULTRA CAMEO CAC sseerrttnumber</span>
				            <span class="collec_measure">5.5 mm</span>
				            <span class="collec_serial">sseerrialnumber</span>
				            <span class="collec_internal">inttternnaaalIIIDDD</span>
				            <span class="collec_storage">Ushkov</span>
				            <span class="collec_price">RUB 1020.00, Meshok, 14 May 2026</span>
				            <span class="collec_comment">Some private comment</span>
				            <span class="collec_swap_comment">Some public comment</span>
				        </div>
				    </td>
				    <td colspan="3">
				        <button class="collec_edit" title="Edit" onclick="collec_modal_new('paper', 216431, 536093, 78514703, 3, 'sup', 0, '1020.00', 'Some private comment', 'Some public comment', 0, ['01ce3c701d804805715f837712a60190fab29146768b235fc0aaf9d7d84844a2.png','bb387e8205c6047025d1d7d9776e24aafc5afe22db9e8f359f7c18e6d5d0b6af.png'], '1', '185', '{&quot;gradingDesignation&quot;:[&quot;359&quot;],&quot;gradingStrike&quot;:&quot;0&quot;,&quot;gradingSurface&quot;:&quot;0&quot;}', 'sseerrttnumber', 'Green', 'Ushkov', 'Meshok', '2026-05-14', 'sseerrialnumber', 'inttternnaaalIIIDDD', '5.5', null, null, ); return false;">
				            <span></span>
				        </button>
				    </td>
				</tr>
				""";

		NumistaCollectionSaveResponse response = parser.parse(html);
		assertNotNull(response);
		assertEquals("78514703", response.getNumistaCollectionItemId());
		assertEquals("216431", response.getCoinId());
		assertEquals("536093", response.getVersionId());
		assertEquals(3, response.getQuantity());
		assertEquals("sup", response.getGradeCode());
		assertEquals("XF", response.getDisplayGrade());
		assertEquals(2, response.getPictures().size());
		assertEquals("Some private comment", response.getComment());
		assertEquals("Some public comment", response.getSwapComment());
		assertEquals("sseerrttnumber", response.getSlabNumber());
		assertEquals("Ushkov", response.getStorageLocation());
		assertTrue(response.getGradingDesignationJson().contains("gradingDesignation"));
	}

	@Test
	void parsesCollecEditOnclickWithNullGrade() {
		String html = """
				<tr>
				    <td colspan="13">
				        <div style="display: flex;">
				            <span class="collec_q col216431">1&times;</span>
				        </div>
				    </td>
				    <td colspan="3">
				        <button class="collec_edit" title="Edit" onclick="collec_modal_new('paper', 216431, 550654, 78515318, 1, null, 0, '2234.00', '', '', 0, [], null, null, '&quot;&quot;', '', null, '', '', null, '', '', null, null, null, ); return false;">
				            <span></span>
				        </button>
				    </td>
				</tr>
				""";

		NumistaCollectionSaveResponse response = parser.parse(html);
		assertNotNull(response);
		assertEquals("216431", response.getCoinId());
		assertEquals("550654", response.getVersionId());
		assertEquals("78515318", response.getNumistaCollectionItemId());
		assertEquals(1, response.getQuantity());
		assertEquals(null, response.getGradeCode());
		assertEquals("2234.00", response.getValue());
		assertTrue(response.getPictures().isEmpty());
	}

	@Test
	void parseCollecModalNewArguments_extractsThreeNids() {
		String onclick = "collec_modal_new('paper', 216431, 550654, 78515318, 1, null, 0, '2234.00', '', '', 0, [], null, null, '&quot;&quot;', '', null, '', '', null, '', '', null, null, null, ); return false;";

		var args = NumistaCollectionSaveResponseParser.parseCollecModalNewArguments(onclick);

		assertEquals("216431", args.get(1).trim());
		assertEquals("550654", args.get(2).trim());
		assertEquals("78515318", args.get(3).trim());
	}
}

