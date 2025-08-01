/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.ui.font.fontmanager.impl;

import net.ccbluex.liquidbounce.ui.font.fontmanager.api.FontFamily;
import net.ccbluex.liquidbounce.ui.font.fontmanager.api.FontManager;
import net.ccbluex.liquidbounce.ui.font.fontmanager.api.FontRenderer;
import net.ccbluex.liquidbounce.ui.font.fontmanager.api.FontType;

@SuppressWarnings("SpellCheckingInspection")
public interface Fonts {

	FontManager fontManager = SimpleFontManager.create();
	static FontManager getFontManager() {
		return fontManager;
	}

	FontManager FONT_MANAGER = getFontManager();

	interface ICONFONT {

		FontFamily ICONFONT = FONT_MANAGER.fontFamily(FontType.ICONFONT);

		final class ICONFONT_20 { public static final FontRenderer ICONFONT_20 = ICONFONT.ofSize(20); private ICONFONT_20() {} }
	}

	interface CheckFont {

		FontFamily CheckFont = FONT_MANAGER.fontFamily(FontType.Check);

		final class CheckFont_20 { public static final FontRenderer CheckFont_20 = CheckFont.ofSize(20); private CheckFont_20() {} }
	}

	interface SF {

		FontFamily SF = FONT_MANAGER.fontFamily(FontType.SF);
		final class SF_14 { public static final FontRenderer SF_14 = SF.ofSize(14); private SF_14() {} }
		final class SF_16 { public static final FontRenderer SF_16 = SF.ofSize(16); private SF_16() {} }
		final class SF_18 { public static final FontRenderer SF_18 = SF.ofSize(18); private SF_18() {} }
		final class SF_20 { public static final FontRenderer SF_20 = SF.ofSize(20); private SF_20() {} }
	}

	interface SFBOLD {

		FontFamily SFBOLD = FONT_MANAGER.fontFamily(FontType.SFBOLD);
		final class SFBOLD_26 { public static final FontRenderer SFBOLD_26 = SFBOLD.ofSize(26); private SFBOLD_26() {} }
		final class SFBOLD_18 { public static final FontRenderer SFBOLD_18 = SFBOLD.ofSize(18); private SFBOLD_18() {} }
	}

}