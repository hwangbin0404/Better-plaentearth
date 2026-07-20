package com.betterpe.brewery;

import java.util.List;

/**
 * Static recipe table transcribed from the server's PlanetEarth brewing guide
 * (양조주 + 증류주 tables). barrelType "a" = any wood, "x" = no barrel/not applicable.
 */
public final class BreweryRecipes {

	private static final List<BreweryRecipe> ALL = List.of(
			// ---- 양조주 (fermented, not distilled) ----
			new BreweryRecipe("맥주", "보리 4, 홉 2", "a", 11, 2, 0, 7.5, 13),
			new BreweryRecipe("크바스", "빵 2, 설탕 3", "가문비나무", 8, 2, 0, 8.4, 16),
			new BreweryRecipe("밀맥주", "밀 6, 홉 2", "a", 11, 3, 0, 9.8, 13),
			new BreweryRecipe("크릭 맥주", "보리 6, 홉 2, 달콤한 열매 1", "a", 12, 4, 0, 11.8, 14),
			new BreweryRecipe("흑맥주", "보리 5, 홉 2, 목탄 2", "짙은 참나무", 13, 5, 0, 11.7, 7),
			new BreweryRecipe("버터 맥주", "보리 5, 홉 2, 버터 1, 계란 1", "a", 10, 5, 0, 14.4, 15),

			new BreweryRecipe("민들레주", "민들레 8, 설탕 4", "a", 16, 8, 0, 18.9, 13),
			new BreweryRecipe("사과주", "사과 4, 설탕 6", "a", 12, 8, 0, 36.0, 10),
			new BreweryRecipe("망고 사이다", "망고 10, 설탕 3", "정글 나무", 9, 7, 0, 14.3, 7),
			new BreweryRecipe("빛나는 베리주", "달콤한 열매 10, 발광 열매 1, 설탕 2", "a", 8, 6, 0, 11.0, 7),

			new BreweryRecipe("압생트", "독감자 2, 잔디 6, 갈색 버섯 1", "a", 16, 5, 0, 12.2, 8),
			new BreweryRecipe("폴케", "선인장 8, 설탕 4, 갈색 버섯 1", "a", 14, 5, 0, 12.2, 3),

			new BreweryRecipe("레드 와인", "포도 12, 설탕 3", "참나무", 15, 11, 0, 15.3, 8),
			new BreweryRecipe("샴페인", "포도 12, 설탕 3, 화약 1", "참나무", 11, 10, 0, 16.2, 10),

			new BreweryRecipe("사케", "쌀 10, 갈색버섯 2", "a", 14, 9, 0, 13.9, 16),
			new BreweryRecipe("막걸리", "쌀 8, 갈색 버섯 1, 설탕 1 (20분 이상 숙성 필수)", "a", 12, 4, 0, 11.2, 8),

			new BreweryRecipe("염화주", "네더와트 14, 석탄 4, 부싯돌 1", "진홍빛 나무", 12, 66, 0, 18, 20),
			new BreweryRecipe("한탄주", "차가운 심장 2, 네더와트 10", "뒤틀린 나무", 15, 12, 0, 52.0, 25),

			new BreweryRecipe("핫초코", "코코아콩 32 (성급함 35초)", "x", 14, 0, 0, 0, -6),

			// ---- 증류주 (distilled spirits) ----
			new BreweryRecipe("위스키", "밀 8, 갈색 버섯 1", "참나무", 10, 12, 3, 15.9, 40),
			new BreweryRecipe("버번 위스키", "옥수수 10, 밀 4", "참나무", 8, 8, 5, 16.3, 50),
			new BreweryRecipe("피트 위스키", "밀 6, 옥수수 3, 갈색 버섯 1, 석탄 1", "자작나무", 9, 10, 3, 17.5, 40),
			new BreweryRecipe("보드카", "감자 12, 갈색 버섯 1", "x", 4, 0, 6, 9.3, 45),
			new BreweryRecipe("브랜디", "포도 12, 설탕 6", "참나무", 12, 10, 3, 17.6, 40),
			new BreweryRecipe("럼", "사탕수수 12, 설탕 4", "짙은 참나무", 10, 4, 4, 15.0, 40),
			new BreweryRecipe("소주", "쌀 12, 밀 1, 갈색버섯 1", "a", 16, 8, 3, 16.8, 35)
	);

	private BreweryRecipes() {
	}

	public static List<BreweryRecipe> all() {
		return ALL;
	}

	/** Exact normalized-name match first, then a contains() fallback. */
	public static BreweryRecipe find(String query) {
		String key = BreweryRecipe.normalize(query);
		if (key.isEmpty()) {
			return null;
		}
		for (BreweryRecipe r : ALL) {
			if (BreweryRecipe.normalize(r.name).equals(key)) {
				return r;
			}
		}
		for (BreweryRecipe r : ALL) {
			if (BreweryRecipe.normalize(r.name).contains(key)) {
				return r;
			}
		}
		return null;
	}
}
