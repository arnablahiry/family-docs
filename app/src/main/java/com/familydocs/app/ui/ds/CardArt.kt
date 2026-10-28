package com.familydocs.app.ui.ds
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.familydocs.app.R

// Generated from the demo's cardart.js (palettes) and the flags (country palettes). Same scene as the demo:
// sky gradient, hill 1, the type's motif image, hills 2 and 3.

/** Documents themed by the issuing country's flag. */
val NATIONAL_CARD_TYPES = setOf("passport", "visa", "residence", "aadhaar", "pan", "voter", "driving", "id", "social")

private val TYPE_PALETTES: Map<String, LongArray> = mapOf(
    "passport" to longArrayOf(0xFF4A5E84, 0xFF2E3D5E, 0xFF3A4C70, 0xFF2D3C5A, 0xFF233049),
    "visa" to longArrayOf(0xFF7E6C9C, 0xFF594A78, 0xFF685985, 0xFF54476D, 0xFF423858),
    "residence" to longArrayOf(0xFF6C948E, 0xFF47706B, 0xFF557D77, 0xFF44665F, 0xFF35524C),
    "aadhaar" to longArrayOf(0xFFB98A66, 0xFF8F6247, 0xFF9A6A4C, 0xFF7D543B, 0xFF63412E),
    "pan" to longArrayOf(0xFF5D7895, 0xFF3F5873, 0xFF4C6582, 0xFF3C516B, 0xFF2F4056),
    "voter" to longArrayOf(0xFF7F8A5C, 0xFF5A6440, 0xFF6C7650, 0xFF586140, 0xFF454D32),
    "driving" to longArrayOf(0xFFA6786A, 0xFF7C5347, 0xFF8C6052, 0xFF734C40, 0xFF5C3C32),
    "id" to longArrayOf(0xFF6A7896, 0xFF4A5672, 0xFF58657F, 0xFF475268, 0xFF384154),
    "social" to longArrayOf(0xFF6A8296, 0xFF4A6072, 0xFF58707F, 0xFF475B68, 0xFF384854),
    "insurance" to longArrayOf(0xFF6A9080, 0xFF47685A, 0xFF567A6A, 0xFF445F53, 0xFF344A40),
    "vaccine" to longArrayOf(0xFFA87A8C, 0xFF7C5466, 0xFF8F6474, 0xFF76505E, 0xFF5E3F4B),
    "medical" to longArrayOf(0xFFA3716B, 0xFF784E49, 0xFF8A5E58, 0xFF714B46, 0xFF5A3B37),
    "bank" to longArrayOf(0xFF6A6B9C, 0xFF4A4A76, 0xFF5A5B88, 0xFF48486F, 0xFF393958),
    "education" to longArrayOf(0xFF988459, 0xFF6E5E3C, 0xFF7F6E49, 0xFF68593B, 0xFF52462E),
    "certificate" to longArrayOf(0xFF9C7A62, 0xFF725642, 0xFF84654F, 0xFF6C513F, 0xFF563F31),
    "property" to longArrayOf(0xFF7C7398, 0xFF585073, 0xFF675F84, 0xFF544D6C, 0xFF423C56),
    "bill" to longArrayOf(0xFFA28C5E, 0xFF776440, 0xFF8A7650, 0xFF71603F, 0xFF5A4C31),
    "tax" to longArrayOf(0xFF5E7B8F, 0xFF41596B, 0xFF4D687B, 0xFF3E5465, 0xFF314352),
)

/** Muted colours from each country's flag: sky, sky 2, hill 1, hill 2, hill 3. */
private val COUNTRY_PALETTES: Map<String, LongArray> by lazy { mapOf(
    "AD" to longArrayOf(0xFFA86C79, 0xFF784B54, 0xFF575D80, 0xFF444966, 0xFF33374F),
    "AE" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF578067, 0xFF446651, 0xFF334F3E),
    "AF" to longArrayOf(0xFF6CA86C, 0xFF4B784B, 0xFF805757, 0xFF664444, 0xFF4F3333),
    "AG" to longArrayOf(0xFFA86C72, 0xFF784B50, 0xFF798075, 0xFF60665D, 0xFF4A4F48),
    "AI" to longArrayOf(0xFF6C7FA8, 0xFF4B5978, 0xFF7E7580, 0xFF655D66, 0xFF4E484F),
    "AL" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF805757, 0xFF664444, 0xFF4F3333),
    "AM" to longArrayOf(0xFFA86C71, 0xFF784B4E, 0xFF576380, 0xFF444E66, 0xFF333C4F),
    "AO" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF575780, 0xFF444466, 0xFF33334F),
    "AQ" to longArrayOf(0xFF6C87A8, 0xFF4B5F78, 0xFF576980, 0xFF445366, 0xFF33404F),
    "AR" to longArrayOf(0xFF8095A8, 0xFF5A6978, 0xFF627180, 0xFF4D5A66, 0xFF3B454F),
    "AS" to longArrayOf(0xFF6C6CA8, 0xFF4B4B78, 0xFF80615E, 0xFF664C4A, 0xFF4F3A38),
    "AT" to longArrayOf(0xFFA86C76, 0xFF784B52, 0xFF80575D, 0xFF664449, 0xFF4F3338),
    "AU" to longArrayOf(0xFF6C6CA8, 0xFF4B4B78, 0xFF80757D, 0xFF665D64, 0xFF4F484D),
    "AW" to longArrayOf(0xFF6C94A8, 0xFF4B6978, 0xFF68806F, 0xFF526658, 0xFF3F4F44),
    "AX" to longArrayOf(0xFF6C8AA8, 0xFF4B6178, 0xFF807357, 0xFF665B44, 0xFF4F4633),
    "AZ" to longArrayOf(0xFFA86C71, 0xFF784B4E, 0xFF577880, 0xFF445F66, 0xFF334A4F),
    "BA" to longArrayOf(0xFFA89C6C, 0xFF786F4B, 0xFF575780, 0xFF444466, 0xFF33334F),
    "BB" to longArrayOf(0xFF6C7DA8, 0xFF4B5878, 0xFF807557, 0xFF665D44, 0xFF4F4833),
    "BD" to longArrayOf(0xFFA86C72, 0xFF784B4F, 0xFF578075, 0xFF44665D, 0xFF334F48),
    "BE" to longArrayOf(0xFFA86C72, 0xFF784B4F, 0xFF807957, 0xFF666144, 0xFF4F4B33),
    "BF" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF648057, 0xFF4F6644, 0xFF3C4F33),
    "BG" to longArrayOf(0xFF6CA898, 0xFF4B786C, 0xFF805B57, 0xFF664744, 0xFF4F3633),
    "BH" to longArrayOf(0xFFA86C72, 0xFF784B4F, 0xFF80575B, 0xFF664447, 0xFF4F3336),
    "BI" to longArrayOf(0xFFA86C73, 0xFF784B50, 0xFF57805F, 0xFF44664B, 0xFF334F39),
    "BJ" to longArrayOf(0xFF7FA86C, 0xFF59784B, 0xFF807957, 0xFF666044, 0xFF4F4B33),
    "BL" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF575780, 0xFF444466, 0xFF33334F),
    "BM" to longArrayOf(0xFFA86C73, 0xFF784B50, 0xFF807576, 0xFF665D5E, 0xFF4F4848),
    "BN" to longArrayOf(0xFFA8A26C, 0xFF78734B, 0xFF805759, 0xFF664446, 0xFF4F3335),
    "BO" to longArrayOf(0xFFA8706C, 0xFF784E4B, 0xFF807B57, 0xFF666244, 0xFF4F4C33),
    "BQ" to longArrayOf(0xFFA86C71, 0xFF784B4F, 0xFF626280, 0xFF4D4E66, 0xFF3B3B4F),
    "BR" to longArrayOf(0xFF6CA87D, 0xFF4B7858, 0xFF807D57, 0xFF666444, 0xFF4F4D33),
    "BS" to longArrayOf(0xFF6CA6A8, 0xFF4B7678, 0xFF718057, 0xFF5A6644, 0xFF454F34),
    "BT" to longArrayOf(0xFFA87B6C, 0xFF78564B, 0xFF807857, 0xFF666044, 0xFF4F4A33),
    "BV" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF5A5D80, 0xFF474966, 0xFF36384F),
    "BW" to longArrayOf(0xFF6C9CA8, 0xFF4B6F78, 0xFF575780, 0xFF444466, 0xFF33334F),
    "BY" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF5D8067, 0xFF496652, 0xFF384F3F),
    "BZ" to longArrayOf(0xFF6C88A8, 0xFF4B6078, 0xFF76806E, 0xFF5E6658, 0xFF494F43),
    "CA" to longArrayOf(0xFFA8706C, 0xFF784E4B, 0xFF80706F, 0xFF665958, 0xFF4F4544),
    "CC" to longArrayOf(0xFF6CA86C, 0xFF4B784B, 0xFF6C8057, 0xFF566644, 0xFF424F33),
    "CD" to longArrayOf(0xFF6C8AA8, 0xFF4B6178, 0xFF805757, 0xFF664444, 0xFF4F3334),
    "CF" to longArrayOf(0xFF6DA86C, 0xFF4B784B, 0xFF807F57, 0xFF666644, 0xFF4F4F33),
    "CG" to longArrayOf(0xFF76A86C, 0xFF53784B, 0xFF7F8057, 0xFF666644, 0xFF4F4F33),
    "CH" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF805757, 0xFF664444, 0xFF4F3333),
    "CI" to longArrayOf(0xFF6CA86C, 0xFF4B784B, 0xFF806F57, 0xFF665944, 0xFF4F4433),
    "CK" to longArrayOf(0xFF6C6CA8, 0xFF4B4B78, 0xFF785780, 0xFF604466, 0xFF4A334F),
    "CL" to longArrayOf(0xFFA8706C, 0xFF784E4B, 0xFF575F80, 0xFF444B66, 0xFF33394F),
    "CM" to longArrayOf(0xFFA86C72, 0xFF784B4F, 0xFF807857, 0xFF666044, 0xFF4F4A33),
    "CN" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF805759, 0xFF664445, 0xFF4F3335),
    "CO" to longArrayOf(0xFFA8A36C, 0xFF78744B, 0xFF575C80, 0xFF444866, 0xFF33374F),
    "CP" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF575780, 0xFF444466, 0xFF33334F),
    "CR" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF575780, 0xFF444466, 0xFF33334F),
    "CU" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF576280, 0xFF444E66, 0xFF333B4F),
    "CV" to longArrayOf(0xFF6C75A8, 0xFF4B5278, 0xFF807578, 0xFF665D60, 0xFF4F484A),
    "CW" to longArrayOf(0xFF6C80A8, 0xFF4B5A78, 0xFF7E805B, 0xFF656647, 0xFF4E4F36),
    "CX" to longArrayOf(0xFF6C77A8, 0xFF4B5378, 0xFF578065, 0xFF446650, 0xFF334F3D),
    "CY" to longArrayOf(0xFFA88E6C, 0xFF78644B, 0xFF806E57, 0xFF665744, 0xFF4F4333),
    "CZ" to longArrayOf(0xFFA86C6E, 0xFF784B4C, 0xFF576A80, 0xFF445466, 0xFF33414F),
    "DE" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF807757, 0xFF665F44, 0xFF4F4933),
    "DG" to longArrayOf(0xFF6C6DA8, 0xFF4B4C78, 0xFF805A6F, 0xFF664758, 0xFF4F3644),
    "DJ" to longArrayOf(0xFF6CA86C, 0xFF4B784B, 0xFF5B6780, 0xFF475266, 0xFF363E4F),
    "DK" to longArrayOf(0xFFA86C76, 0xFF784B52, 0xFF806D70, 0xFF665659, 0xFF4F4245),
    "DM" to longArrayOf(0xFF72A86C, 0xFF4F784B, 0xFF805D57, 0xFF664944, 0xFF4F3833),
    "DO" to longArrayOf(0xFFA86C72, 0xFF784B4F, 0xFF576980, 0xFF445366, 0xFF33404F),
    "DZ" to longArrayOf(0xFF6CA88B, 0xFF4B7862, 0xFF807A6F, 0xFF666158, 0xFF4F4B44),
    "EC" to longArrayOf(0xFFA8A36C, 0xFF78744B, 0xFF805757, 0xFF664444, 0xFF4F3334),
    "EE" to longArrayOf(0xFF6C8CA8, 0xFF4B6378, 0xFF575780, 0xFF444466, 0xFF33334F),
    "EG" to longArrayOf(0xFFA86C72, 0xFF784B4F, 0xFF575780, 0xFF444466, 0xFF33334F),
    "EH" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF57806B, 0xFF446655, 0xFF334F41),
    "ER" to longArrayOf(0xFFA86C78, 0xFF784B54, 0xFF598068, 0xFF456652, 0xFF354F3F),
    "ES" to longArrayOf(0xFFA89B6C, 0xFF786E4B, 0xFF805758, 0xFF664445, 0xFF4F3334),
    "ET" to longArrayOf(0xFFA86E6C, 0xFF784C4B, 0xFF807457, 0xFF665D44, 0xFF4F4833),
    "EU" to longArrayOf(0xFF6C80A8, 0xFF4B5978, 0xFF576480, 0xFF444F66, 0xFF333C4F),
    "FI" to longArrayOf(0xFF6C85A8, 0xFF4B5D78, 0xFF576880, 0xFF445266, 0xFF333F4F),
    "FJ" to longArrayOf(0xFF7A9AA8, 0xFF566D78, 0xFF805A63, 0xFF66464E, 0xFF4F353B),
    "FK" to longArrayOf(0xFF6C7FA8, 0xFF4B5978, 0xFF767580, 0xFF5E5D66, 0xFF49484F),
    "FM" to longArrayOf(0xFF7C90A8, 0xFF576578, 0xFF606E80, 0xFF4B5766, 0xFF39434F),
    "FO" to longArrayOf(0xFFA86C7B, 0xFF784B56, 0xFF5D5E80, 0xFF494A66, 0xFF37384F),
    "FR" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF575780, 0xFF444466, 0xFF33334F),
    "GA" to longArrayOf(0xFFA8A36C, 0xFF78744B, 0xFF576E80, 0xFF445766, 0xFF33434F),
    "GB" to longArrayOf(0xFFA86C76, 0xFF784B52, 0xFF786780, 0xFF605166, 0xFF4A3E4F),
    "GD" to longArrayOf(0xFFA86C70, 0xFF784B4E, 0xFF578076, 0xFF44665E, 0xFF334F49),
    "GE" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF806E6E, 0xFF665757, 0xFF4F4343),
    "GF" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF575780, 0xFF444466, 0xFF33334F),
    "GG" to longArrayOf(0xFFA86C71, 0xFF784B4E, 0xFF806D57, 0xFF665644, 0xFF4F4233),
    "GH" to longArrayOf(0xFFA86C72, 0xFF784B4F, 0xFF807857, 0xFF666044, 0xFF4F4A33),
    "GI" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF807575, 0xFF665D5D, 0xFF4F4848),
    "GL" to longArrayOf(0xFFA86C78, 0xFF784B53, 0xFF80575F, 0xFF66444A, 0xFF4F3339),
    "GM" to longArrayOf(0xFF6C6CA8, 0xFF4B4B78, 0xFF805757, 0xFF664444, 0xFF4F3333),
    "GN" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF808057, 0xFF666644, 0xFF4F4F33),
    "GP" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF575780, 0xFF444466, 0xFF33334F),
    "GQ" to longArrayOf(0xFFA86F6C, 0xFF784D4B, 0xFF688057, 0xFF526644, 0xFF3F4F33),
    "GR" to longArrayOf(0xFF6C8AA8, 0xFF4B6178, 0xFF576B80, 0xFF445566, 0xFF33414F),
    "GS" to longArrayOf(0xFF6C6CA8, 0xFF4B4B78, 0xFF80577B, 0xFF664462, 0xFF4F334C),
    "GT" to longArrayOf(0xFF7292A8, 0xFF4F6778, 0xFF757E80, 0xFF5D6566, 0xFF484E4F),
    "GU" to longArrayOf(0xFF7283A8, 0xFF505C78, 0xFF805871, 0xFF664459, 0xFF4F3445),
    "GW" to longArrayOf(0xFFA89D6C, 0xFF78704B, 0xFF578069, 0xFF446653, 0xFF334F40),
    "GY" to longArrayOf(0xFF81A86C, 0xFF5A784B, 0xFF805A57, 0xFF664644, 0xFF4F3533),
    "HK" to longArrayOf(0xFFA86C71, 0xFF784B4E, 0xFF806366, 0xFF664E51, 0xFF4F3C3E),
    "HM" to longArrayOf(0xFF6C6CA8, 0xFF4B4B78, 0xFF80757D, 0xFF665D64, 0xFF4F484D),
    "HN" to longArrayOf(0xFF6C9FA8, 0xFF4B7178, 0xFF5C7B80, 0xFF486266, 0xFF374C4F),
    "HR" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF585780, 0xFF454466, 0xFF34334F),
    "HT" to longArrayOf(0xFFA86C77, 0xFF784B53, 0xFF575F80, 0xFF444A66, 0xFF33394F),
    "HU" to longArrayOf(0xFFA8756C, 0xFF78524B, 0xFF678057, 0xFF516644, 0xFF3E4F33),
    "IC" to longArrayOf(0xFFA89C6C, 0xFF786F4B, 0xFF576F80, 0xFF445866, 0xFF33444F),
    "ID" to longArrayOf(0xFFA86C70, 0xFF784B4E, 0xFF80575A, 0xFF664446, 0xFF4F3335),
    "IE" to longArrayOf(0xFFA8886C, 0xFF78604B, 0xFF57806A, 0xFF446654, 0xFF334F40),
    "IL" to longArrayOf(0xFF6C7EA8, 0xFF4B5878, 0xFF576380, 0xFF444E66, 0xFF333C4F),
    "IM" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF806C67, 0xFF665552, 0xFF4F413F),
    "IN" to longArrayOf(0xFFA88D71, 0xFF78634E, 0xFF588057, 0xFF456644, 0xFF344F33),
    "IO" to longArrayOf(0xFF6C6DA8, 0xFF4B4C78, 0xFF805A6F, 0xFF664758, 0xFF4F3644),
    "IQ" to longArrayOf(0xFFA86C72, 0xFF784B4F, 0xFF575780, 0xFF444466, 0xFF33334F),
    "IR" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF578060, 0xFF44664C, 0xFF334F3A),
    "IS" to longArrayOf(0xFF6C82A8, 0xFF4B5B78, 0xFF805969, 0xFF664553, 0xFF4F3540),
    "IT" to longArrayOf(0xFFA86C70, 0xFF784B4E, 0xFF57806A, 0xFF446654, 0xFF334F40),
    "JE" to longArrayOf(0xFFA86C73, 0xFF784B50, 0xFF807673, 0xFF665E5C, 0xFF4F4947),
    "JM" to longArrayOf(0xFF7FA86C, 0xFF59784B, 0xFF807857, 0xFF666044, 0xFF4F4A33),
    "JO" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF578057, 0xFF446644, 0xFF334F33),
    "JP" to longArrayOf(0xFFA86C7B, 0xFF784B56, 0xFF805963, 0xFF66454E, 0xFF4F353C),
    "KE" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF578057, 0xFF446644, 0xFF334F33),
    "KG" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF806F57, 0xFF665844, 0xFF4F4433),
    "KH" to longArrayOf(0xFF6C7CA8, 0xFF4B5778, 0xFF80575D, 0xFF664449, 0xFF4F3338),
    "KI" to longArrayOf(0xFFA8726C, 0xFF784F4B, 0xFF758077, 0xFF5D665F, 0xFF484F49),
    "KM" to longArrayOf(0xFFA8A86C, 0xFF78784B, 0xFF578064, 0xFF44664F, 0xFF334F3C),
    "KN" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF648057, 0xFF4F6644, 0xFF3C4F33),
    "KP" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF616580, 0xFF4C5066, 0xFF3A3D4F),
    "KR" to longArrayOf(0xFFA886A6, 0xFF785E76, 0xFF757680, 0xFF5D5E66, 0xFF48494F),
    "KW" to longArrayOf(0xFFA86C72, 0xFF784B50, 0xFF578060, 0xFF44664C, 0xFF334F3A),
    "KY" to longArrayOf(0xFF6C6CA8, 0xFF4B4B78, 0xFF80596F, 0xFF664558, 0xFF4F3544),
    "KZ" to longArrayOf(0xFF6CA1A8, 0xFF4B7378, 0xFF668067, 0xFF516652, 0xFF3E4F3F),
    "LA" to longArrayOf(0xFFA86C72, 0xFF784B4F, 0xFF576680, 0xFF445166, 0xFF333E4F),
    "LB" to longArrayOf(0xFFA86C6E, 0xFF784B4C, 0xFF807D6A, 0xFF666454, 0xFF4F4E40),
    "LC" to longArrayOf(0xFF7598A8, 0xFF526C78, 0xFF7B806F, 0xFF636658, 0xFF4C4F44),
    "LI" to longArrayOf(0xFFA86C72, 0xFF784B4F, 0xFF576680, 0xFF445066, 0xFF333D4F),
    "LK" to longArrayOf(0xFFA86C73, 0xFF784B50, 0xFF807357, 0xFF665B44, 0xFF4F4633),
    "LR" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF805771, 0xFF66445A, 0xFF4F3345),
    "LS" to longArrayOf(0xFF6C78A8, 0xFF4B5378, 0xFF57806C, 0xFF446656, 0xFF334F42),
    "LT" to longArrayOf(0xFFA8976C, 0xFF786B4B, 0xFF805758, 0xFF664445, 0xFF4F3334),
    "LU" to longArrayOf(0xFF6C98A8, 0xFF4B6C78, 0xFF80575A, 0xFF664447, 0xFF4F3335),
    "LV" to longArrayOf(0xFFA86C76, 0xFF784B52, 0xFF806B6F, 0xFF665558, 0xFF4F4144),
    "LY" to longArrayOf(0xFFA86C71, 0xFF784B4E, 0xFF578063, 0xFF44664E, 0xFF334F3B),
    "MA" to longArrayOf(0xFFA86C6E, 0xFF784B4C, 0xFF805758, 0xFF664445, 0xFF4F3334),
    "MC" to longArrayOf(0xFFA86C72, 0xFF784B50, 0xFF80575B, 0xFF664448, 0xFF4F3336),
    "MD" to longArrayOf(0xFFA8706C, 0xFF784E4B, 0xFF576480, 0xFF444F66, 0xFF333C4F),
    "ME" to longArrayOf(0xFFA86C6E, 0xFF784B4C, 0xFF806C57, 0xFF665544, 0xFF4F4233),
    "MF" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF575780, 0xFF444466, 0xFF33334F),
    "MG" to longArrayOf(0xFFA86F6C, 0xFF784D4B, 0xFF578069, 0xFF446653, 0xFF334F40),
    "MH" to longArrayOf(0xFF7383A8, 0xFF505C78, 0xFF807B75, 0xFF66635D, 0xFF4F4C48),
    "MK" to longArrayOf(0xFFA86D6C, 0xFF784B4B, 0xFF807957, 0xFF666044, 0xFF4F4A33),
    "ML" to longArrayOf(0xFFA8A86C, 0xFF78784B, 0xFF805757, 0xFF664444, 0xFF4F3333),
    "MM" to longArrayOf(0xFFA89B6C, 0xFF786E4B, 0xFF80575B, 0xFF664447, 0xFF4F3336),
    "MN" to longArrayOf(0xFFA86C71, 0xFF784B4F, 0xFF607880, 0xFF4B5F66, 0xFF394A4F),
    "MO" to longArrayOf(0xFF6CA89B, 0xFF4B786E, 0xFF668078, 0xFF516660, 0xFF3E4F4A),
    "MP" to longArrayOf(0xFF6C90A8, 0xFF4B6678, 0xFF758080, 0xFF5D6666, 0xFF484F4F),
    "MQ" to longArrayOf(0xFF6CA889, 0xFF4B7861, 0xFF805759, 0xFF664445, 0xFF4F3335),
    "MR" to longArrayOf(0xFF6CA88B, 0xFF4B7862, 0xFF80575A, 0xFF664447, 0xFF4F3335),
    "MS" to longArrayOf(0xFF6C7FA8, 0xFF4B5978, 0xFF6C7B80, 0xFF556266, 0xFF424C4F),
    "MT" to longArrayOf(0xFFA86C73, 0xFF784B50, 0xFF80575C, 0xFF664448, 0xFF4F3337),
    "MU" to longArrayOf(0xFFA89C6C, 0xFF786F4B, 0xFF80575A, 0xFF664447, 0xFF4F3336),
    "MV" to longArrayOf(0xFFA86C77, 0xFF784B53, 0xFF57806A, 0xFF446654, 0xFF334F40),
    "MW" to longArrayOf(0xFFA86F6C, 0xFF784D4B, 0xFF578061, 0xFF44664D, 0xFF334F3B),
    "MX" to longArrayOf(0xFFA86C72, 0xFF784B4F, 0xFF578072, 0xFF44665A, 0xFF334F46),
    "MY" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF5C5780, 0xFF484466, 0xFF37334F),
    "MZ" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF807657, 0xFF665E44, 0xFF4F4833),
    "NA" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF648057, 0xFF4F6644, 0xFF3C4F33),
    "NC" to longArrayOf(0xFF6C7EA8, 0xFF4B5878, 0xFF807C57, 0xFF666344, 0xFF4F4D33),
    "NE" to longArrayOf(0xFFA8816C, 0xFF785B4B, 0xFF57805F, 0xFF44664B, 0xFF334F39),
    "NF" to longArrayOf(0xFF77A86C, 0xFF53784B, 0xFF6D8069, 0xFF566653, 0xFF424F3F),
    "NG" to longArrayOf(0xFF6CA891, 0xFF4B7866, 0xFF57806F, 0xFF446659, 0xFF334F44),
    "NI" to longArrayOf(0xFF6C8BA8, 0xFF4B6278, 0xFF758077, 0xFF5D665F, 0xFF484F49),
    "NL" to longArrayOf(0xFFA86C71, 0xFF784B4F, 0xFF626280, 0xFF4D4E66, 0xFF3B3B4F),
    "NO" to longArrayOf(0xFFA86C71, 0xFF784B4E, 0xFF7A5E80, 0xFF614A66, 0xFF4B384F),
    "NP" to longArrayOf(0xFFA86C71, 0xFF784B4E, 0xFF805D63, 0xFF66494E, 0xFF4F373C),
    "NR" to longArrayOf(0xFF6C7DA8, 0xFF4B5778, 0xFF757980, 0xFF5D6066, 0xFF484A4F),
    "NU" to longArrayOf(0xFFA8A16C, 0xFF78724B, 0xFF806F58, 0xFF665844, 0xFF4F4434),
    "NZ" to longArrayOf(0xFF6C7DA8, 0xFF4B5878, 0xFF777180, 0xFF5F5A66, 0xFF4A454F),
    "OM" to longArrayOf(0xFFA86D6C, 0xFF784B4B, 0xFF57805C, 0xFF446648, 0xFF334F37),
    "PA" to longArrayOf(0xFF6C6CA8, 0xFF4B4B78, 0xFF805757, 0xFF664444, 0xFF4F3333),
    "PC" to longArrayOf(0xFF6C82A8, 0xFF4B5B78, 0xFF576680, 0xFF445066, 0xFF333D4F),
    "PE" to longArrayOf(0xFFA86C71, 0xFF784B4F, 0xFF80575A, 0xFF664447, 0xFF4F3336),
    "PF" to longArrayOf(0xFFA8706C, 0xFF784E4B, 0xFF805D5A, 0xFF664947, 0xFF4F3736),
    "PG" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF575780, 0xFF444466, 0xFF33334F),
    "PH" to longArrayOf(0xFFA86C72, 0xFF784B4F, 0xFF576480, 0xFF444F66, 0xFF333D4F),
    "PK" to longArrayOf(0xFF6CA86C, 0xFF4B784B, 0xFF758075, 0xFF5D665D, 0xFF484F48),
    "PL" to longArrayOf(0xFFA86C78, 0xFF784B54, 0xFF80575F, 0xFF66444B, 0xFF4F3339),
    "PM" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF575780, 0xFF444466, 0xFF33334F),
    "PN" to longArrayOf(0xFF6C7DA8, 0xFF4B5878, 0xFF7F8075, 0xFF66665D, 0xFF4F4F48),
    "PR" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF576480, 0xFF444F66, 0xFF333D4F),
    "PS" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF5B8057, 0xFF476644, 0xFF364F33),
    "PT" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF638057, 0xFF4E6644, 0xFF3C4F33),
    "PW" to longArrayOf(0xFF7198A8, 0xFF4F6C78, 0xFF78805E, 0xFF60664A, 0xFF4A4F38),
    "PY" to longArrayOf(0xFFA8706C, 0xFF784E4B, 0xFF576480, 0xFF444F66, 0xFF333D4F),
    "QA" to longArrayOf(0xFFA86C7E, 0xFF784B58, 0xFF806D73, 0xFF66565B, 0xFF4F4246),
    "RE" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF575780, 0xFF444466, 0xFF33334F),
    "RO" to longArrayOf(0xFFA8706C, 0xFF784E4B, 0xFF807A57, 0xFF666244, 0xFF4F4C33),
    "RS" to longArrayOf(0xFFA86C6E, 0xFF784B4C, 0xFF576A80, 0xFF445466, 0xFF33404F),
    "RU" to longArrayOf(0xFF6C81A8, 0xFF4B5A78, 0xFF805A57, 0xFF664644, 0xFF4F3533),
    "RW" to longArrayOf(0xFF6C97A8, 0xFF4B6B78, 0xFF807A57, 0xFF666144, 0xFF4F4B33),
    "SA" to longArrayOf(0xFF6CA882, 0xFF4B785B, 0xFF6E8075, 0xFF58665D, 0xFF434F48),
    "SB" to longArrayOf(0xFF6C6CA8, 0xFF4B4B78, 0xFF578057, 0xFF446644, 0xFF334F33),
    "SC" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF6C6480, 0xFF554F66, 0xFF423C4F),
    "SD" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF588057, 0xFF456644, 0xFF344F33),
    "SE" to longArrayOf(0xFF6C8DA8, 0xFF4B6478, 0xFF807857, 0xFF665F44, 0xFF4F4A33),
    "SG" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF805757, 0xFF664444, 0xFF4F3333),
    "SH" to longArrayOf(0xFFA86C76, 0xFF784B52, 0xFF786780, 0xFF605166, 0xFF4A3E4F),
    "SI" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF575780, 0xFF444466, 0xFF33334F),
    "SJ" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF807073, 0xFF66595B, 0xFF4F4546),
    "SK" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF576980, 0xFF445366, 0xFF33404F),
    "SL" to longArrayOf(0xFF6CA86C, 0xFF4B784B, 0xFF575780, 0xFF444466, 0xFF33334F),
    "SM" to longArrayOf(0xFF6C99A8, 0xFF4B6D78, 0xFF5E807B, 0xFF4A6663, 0xFF394F4C),
    "SN" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF7F8057, 0xFF656644, 0xFF4F4F33),
    "SO" to longArrayOf(0xFF6C8CA8, 0xFF4B6378, 0xFF6F7980, 0xFF586066, 0xFF444A4F),
    "SR" to longArrayOf(0xFFA86C78, 0xFF784B54, 0xFF608063, 0xFF4B664E, 0xFF394F3B),
    "SS" to longArrayOf(0xFF6C85A8, 0xFF4B5D78, 0xFF578064, 0xFF44664F, 0xFF334F3C),
    "ST" to longArrayOf(0xFF6CA875, 0xFF4B7852, 0xFF807857, 0xFF665F44, 0xFF4F4A33),
    "SV" to longArrayOf(0xFF6C81A8, 0xFF4B5A78, 0xFF626E80, 0xFF4D5766, 0xFF3B434F),
    "SX" to longArrayOf(0xFFA86C71, 0xFF784B4E, 0xFF576080, 0xFF444B66, 0xFF33394F),
    "SY" to longArrayOf(0xFFA86C72, 0xFF784B4F, 0xFF575780, 0xFF444466, 0xFF33334F),
    "SZ" to longArrayOf(0xFFA86E6C, 0xFF784C4B, 0xFF576280, 0xFF444E66, 0xFF333B4F),
    "TC" to longArrayOf(0xFF6C82A8, 0xFF4B5C78, 0xFF807967, 0xFF666051, 0xFF4F4A3E),
    "TD" to longArrayOf(0xFFA89C6C, 0xFF786F4B, 0xFF80575F, 0xFF66444B, 0xFF4F3339),
    "TF" to longArrayOf(0xFF6C79A8, 0xFF4B5578, 0xFF6D6A80, 0xFF575466, 0xFF43404F),
    "TG" to longArrayOf(0xFFA8A26C, 0xFF78734B, 0xFF5B8057, 0xFF486644, 0xFF364F33),
    "TH" to longArrayOf(0xFFA86C77, 0xFF784B53, 0xFF676580, 0xFF514F66, 0xFF3E3D4F),
    "TJ" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF578057, 0xFF446644, 0xFF334F33),
    "TK" to longArrayOf(0xFF6C7DA8, 0xFF4B5878, 0xFF807957, 0xFF666044, 0xFF4F4B33),
    "TL" to longArrayOf(0xFFA86C70, 0xFF784B4D, 0xFF807257, 0xFF665B44, 0xFF4F4633),
    "TM" to longArrayOf(0xFF6CA888, 0xFF4B785F, 0xFF7A8065, 0xFF626650, 0xFF4B4F3D),
    "TN" to longArrayOf(0xFFA86C71, 0xFF784B4E, 0xFF805B5F, 0xFF66474A, 0xFF4F3639),
    "TO" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF805757, 0xFF664444, 0xFF4F3333),
    "TR" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF805759, 0xFF664446, 0xFF4F3335),
    "TT" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF805758, 0xFF664445, 0xFF4F3334),
    "TV" to longArrayOf(0xFF6C9CA8, 0xFF4B6E78, 0xFF697080, 0xFF535966, 0xFF3F454F),
    "TW" to longArrayOf(0xFFA86C6C, 0xFF784B4B, 0xFF5A5780, 0xFF464466, 0xFF35334F),
    "TZ" to longArrayOf(0xFF6C90A8, 0xFF4B6678, 0xFF57805C, 0xFF446648, 0xFF334F36),
    "UA" to longArrayOf(0xFF6C89A8, 0xFF4B6078, 0xFF807957, 0xFF666144, 0xFF4F4B33),
    "UG" to longArrayOf(0xFFA8A36C, 0xFF78744B, 0xFF806057, 0xFF664C44, 0xFF4F3A33),
    "UM" to longArrayOf(0xFFA87275, 0xFF784F52, 0xFF586580, 0xFF454F66, 0xFF343D4F),
    "UN" to longArrayOf(0xFF718CA8, 0xFF4E6378, 0xFF576B80, 0xFF445566, 0xFF34414F),
    "US" to longArrayOf(0xFFA87275, 0xFF784F52, 0xFF586580, 0xFF454F66, 0xFF343D4F),
    "UY" to longArrayOf(0xFF6C80A8, 0xFF4B5A78, 0xFF757F80, 0xFF5D6666, 0xFF484F4F),
    "UZ" to longArrayOf(0xFF6CA87C, 0xFF4B7857, 0xFF577980, 0xFF446166, 0xFF334B4F),
    "VA" to longArrayOf(0xFFA8A16C, 0xFF78724B, 0xFF807968, 0xFF666052, 0xFF4F4A3F),
    "VC" to longArrayOf(0xFFA8A76C, 0xFF78774B, 0xFF576E80, 0xFF445866, 0xFF33434F),
    "VE" to longArrayOf(0xFFA86C73, 0xFF784B50, 0xFF807757, 0xFF665F44, 0xFF4F4933),
    "VG" to longArrayOf(0xFF6C6CA8, 0xFF4B4B78, 0xFF805E75, 0xFF66495D, 0xFF4F3848),
    "VI" to longArrayOf(0xFFA8A18C, 0xFF787363, 0xFF807D74, 0xFF66645C, 0xFF4F4D47),
    "VN" to longArrayOf(0xFFA86E6C, 0xFF784C4B, 0xFF806D57, 0xFF665644, 0xFF4F4233),
    "VU" to longArrayOf(0xFFA86C77, 0xFF784B53, 0xFF578069, 0xFF446653, 0xFF334F40),
    "WF" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF575780, 0xFF444466, 0xFF33334F),
    "WS" to longArrayOf(0xFFA86C72, 0xFF784B4F, 0xFF576580, 0xFF445066, 0xFF333D4F),
    "XK" to longArrayOf(0xFF6C7DA8, 0xFF4B5878, 0xFF6D7380, 0xFF565B66, 0xFF42464F),
    "XX" to longArrayOf(0xFF8C94A8, 0xFF626878, 0xFF6B7080, 0xFF545966, 0xFF41454F),
    "YE" to longArrayOf(0xFFA86D6C, 0xFF784B4B, 0xFF575780, 0xFF444466, 0xFF33334F),
    "YT" to longArrayOf(0xFFA86C6F, 0xFF784B4D, 0xFF575780, 0xFF444466, 0xFF33334F),
    "ZA" to longArrayOf(0xFF6CA88E, 0xFF4B7864, 0xFF805957, 0xFF664644, 0xFF4F3533),
    "ZM" to longArrayOf(0xFF77A86C, 0xFF53784B, 0xFF806F57, 0xFF665844, 0xFF4F4433),
    "ZW" to longArrayOf(0xFFA89E6C, 0xFF78704B, 0xFF805757, 0xFF664444, 0xFF4F3333),
) }

private val MOTIFS: Map<String, Int> = mapOf(
    "passport" to R.drawable.card_m_passport,
    "visa" to R.drawable.card_m_visa,
    "residence" to R.drawable.card_m_residence,
    "aadhaar" to R.drawable.card_m_aadhaar,
    "pan" to R.drawable.card_m_pan,
    "voter" to R.drawable.card_m_voter,
    "driving" to R.drawable.card_m_driving,
    "id" to R.drawable.card_m_id,
    "social" to R.drawable.card_m_social,
    "insurance" to R.drawable.card_m_insurance,
    "vaccine" to R.drawable.card_m_vaccine,
    "medical" to R.drawable.card_m_medical,
    "bank" to R.drawable.card_m_bank,
    "education" to R.drawable.card_m_education,
    "certificate" to R.drawable.card_m_certificate,
    "property" to R.drawable.card_m_property,
    "bill" to R.drawable.card_m_bill,
    "tax" to R.drawable.card_m_tax,
)

/** [sky, sky2, hill1, hill2, hill3] for a card: the issuing country's for national documents, otherwise the type's. */
fun cardPalette(key: String, hue: Long, country: String?): List<Color> {
    val p = (if (key in NATIONAL_CARD_TYPES && !country.isNullOrEmpty()) COUNTRY_PALETTES[country.uppercase()] else null) ?: TYPE_PALETTES[key]
    if (p != null) return p.map { Color(it) }
    // Unknown types: plain hills in a muted version of the type's colour (demo fallbackPalette).
    val m = lerp(Color(hue), Color(0xFF8A8F99), .38f)
    fun shade(a: Float) = if (a >= 0) lerp(m, Color.White, a) else lerp(m, Color.Black, -a)
    return listOf(shade(.12f), shade(-.22f), shade(-.08f), shade(-.24f), shade(-.38f))
}

/** The illustrated background of a wallet card (demo `cardSVG`), drawn at any size. */
@Composable
fun CardScene(key: String, hue: Long, country: String?, modifier: Modifier = Modifier) {
    val p = cardPalette(key, hue, country)
    val motif = MOTIFS[key]
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val k = maxOf(size.width / 160f, size.height / 101f) // preserveAspectRatio xMidYMid slice
            val dx = (size.width - 160f * k) / 2f; val dy = (size.height - 101f * k) / 2f
            fun X(x: Float) = dx + x * k; fun Y(y: Float) = dy + y * k
            drawRect(Brush.linearGradient(listOf(p[0], p[1]), Offset(0f, 0f), Offset(size.width * .35f, size.height)))
            drawPath(hill(0, ::X, ::Y), p[2])
        }
        if (motif != null) Image(painterResource(motif), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Canvas(Modifier.fillMaxSize()) {
            val k = maxOf(size.width / 160f, size.height / 101f)
            val dx = (size.width - 160f * k) / 2f; val dy = (size.height - 101f * k) / 2f
            fun X(x: Float) = dx + x * k; fun Y(y: Float) = dy + y * k
            drawPath(hill(1, ::X, ::Y), p[3])
            drawPath(hill(2, ::X, ::Y), p[4])
        }
    }
}

// The demo's three hills, "M0 y0 Q c1 e1 T 160 y2 V101 H0 Z" with the T control point reflected.
private val HILLS = listOf(
    floatArrayOf(68f, 40f, 56f, 80f, 64f, 58f),
    floatArrayOf(80f, 45f, 70f, 95f, 78f, 73f),
    floatArrayOf(90f, 50f, 83f, 100f, 88f, 85f),
)

private fun hill(i: Int, X: (Float) -> Float, Y: (Float) -> Float): Path {
    val h = HILLS[i]
    val y0 = h[0]; val cx = h[1]; val cy = h[2]; val ex = h[3]; val ey = h[4]; val y2 = h[5]
    return Path().apply {
        moveTo(X(0f), Y(y0))
        quadraticBezierTo(X(cx), Y(cy), X(ex), Y(ey))
        quadraticBezierTo(X(2 * ex - cx), Y(2 * ey - cy), X(160f), Y(y2))
        lineTo(X(160f), Y(101f)); lineTo(X(0f), Y(101f)); close()
    }
}
