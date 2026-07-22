package com.betterpe.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Builds the Cloth Config settings screen shown from Mod Menu.
 * Every entry writes straight back into the shared {@link Config} instance,
 * and {@link ConfigManager#save()} runs when the screen is saved.
 */
public final class BetterPeConfigScreen {

	private BetterPeConfigScreen() {
	}

	public static Screen create(Screen parent) {
		Config cfg = ConfigManager.get();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Text.translatable("text.betterpe.title"))
				.setSavingRunnable(ConfigManager::save);

		ConfigEntryBuilder eb = builder.entryBuilder();

		// ---- General ----
		ConfigCategory general = builder.getOrCreateCategory(Text.translatable("text.betterpe.category.general"));
		general.addEntry(eb.startBooleanToggle(Text.literal("모드 활성화"), cfg.modEnabled)
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.modEnabled = v)
				.build());

		// ---- War ----
		ConfigCategory war = builder.getOrCreateCategory(Text.translatable("text.betterpe.category.war"));
		war.addEntry(eb.startBooleanToggle(Text.literal("전쟁 알리미 사용"), cfg.warNotifierEnabled)
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.warNotifierEnabled = v)
				.build());
		war.addEntry(eb.startBooleanToggle(Text.literal("전쟁 보스바 표시"), cfg.warBossBarEnabled)
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.warBossBarEnabled = v)
				.build());
		war.addEntry(eb.startIntSlider(Text.literal("전쟁 시작 시(hour)"), cfg.warStartHour, 0, 23)
				.setDefaultValue(20)
				.setSaveConsumer(v -> cfg.warStartHour = v)
				.build());
		war.addEntry(eb.startIntSlider(Text.literal("전쟁 시작 분(minute)"), cfg.warStartMinute, 0, 59)
				.setDefaultValue(30)
				.setSaveConsumer(v -> cfg.warStartMinute = v)
				.build());

		// ---- Tag ----
		ConfigCategory tag = builder.getOrCreateCategory(Text.translatable("text.betterpe.category.tag"));
		tag.addEntry(eb.startBooleanToggle(Text.literal("국가/마을 태그 사용"), cfg.tagEnabled)
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.tagEnabled = v)
				.build());
		tag.addEntry(eb.startDoubleField(Text.literal("조준 최대 거리(블록)"), cfg.tagMaxDistance)
				.setDefaultValue(48.0)
				.setMin(4.0).setMax(160.0)
				.setSaveConsumer(v -> cfg.tagMaxDistance = v)
				.build());
		tag.addEntry(eb.startBooleanToggle(Text.literal("무소속이면 태그 숨김"), cfg.tagHideUnaffiliated)
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.tagHideUnaffiliated = v)
				.build());
		tag.addEntry(eb.startBooleanToggle(Text.literal("조준 중인 사람만 표시"), cfg.tagCrosshairOnly)
				.setDefaultValue(true)
				.setTooltip(Text.literal("끄면 조준 여부와 상관없이 주변 반경 내 모든 사람에게 표시합니다."))
				.setSaveConsumer(v -> cfg.tagCrosshairOnly = v)
				.build());
		tag.addEntry(eb.startDoubleField(Text.literal("주변 표시 반경(블록)"), cfg.tagNearbyRadius)
				.setDefaultValue(15.0)
				.setMin(1.0).setMax(200.0)
				.setTooltip(Text.literal("'조준 중인 사람만 표시'가 꺼져 있을 때 사용되는 반경입니다."))
				.setSaveConsumer(v -> cfg.tagNearbyRadius = v)
				.build());

		// ---- Location ----
		ConfigCategory location = builder.getOrCreateCategory(Text.translatable("text.betterpe.category.location"));
		location.addEntry(eb.startBooleanToggle(Text.literal("위치 표시 사용"), cfg.locationEnabled)
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.locationEnabled = v)
				.build());
		location.addEntry(eb.startIntField(Text.literal("근처 마을 반경(블록)"), cfg.locationNearbyRadius)
				.setDefaultValue(500)
				.setMin(50).setMax(5000)
				.setSaveConsumer(v -> cfg.locationNearbyRadius = v)
				.build());
		location.addEntry(eb.startIntField(Text.literal("Dynmap 갱신 주기(초)"), cfg.dynmapRefreshSeconds)
				.setDefaultValue(180)
				.setMin(30).setMax(1800)
				.setSaveConsumer(v -> cfg.dynmapRefreshSeconds = v)
				.build());

		// ---- Goods ----
		ConfigCategory goods = builder.getOrCreateCategory(Text.translatable("text.betterpe.category.goods"));
		goods.addEntry(eb.startBooleanToggle(Text.literal("특산품 추천 사용"), cfg.goodsEnabled)
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.goodsEnabled = v)
				.build());
		goods.addEntry(eb.startIntField(Text.literal("정각 정기선 이용시간(분)"), cfg.shipHourlyWindowMinutes)
				.setDefaultValue(10)
				.setMin(1).setMax(60)
				.setSaveConsumer(v -> cfg.shipHourlyWindowMinutes = v)
				.build());
		goods.addEntry(eb.startIntField(Text.literal("저녁 정기선 이용시간(분)"), cfg.shipEveningWindowMinutes)
				.setDefaultValue(20)
				.setMin(1).setMax(60)
				.setSaveConsumer(v -> cfg.shipEveningWindowMinutes = v)
				.build());

		// ---- Chat highlight ----
		ConfigCategory chat = builder.getOrCreateCategory(Text.translatable("text.betterpe.category.chat"));
		chat.addEntry(eb.startBooleanToggle(Text.literal("아군 채팅 강조 사용"), cfg.chatHighlightEnabled)
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.chatHighlightEnabled = v)
				.build());
		chat.addEntry(eb.startStrField(Text.literal("국가원 색 코드"), cfg.nationColor)
				.setDefaultValue("a")
				.setSaveConsumer(v -> cfg.nationColor = v)
				.build());
		chat.addEntry(eb.startStrField(Text.literal("동맹원 색 코드"), cfg.allyColor)
				.setDefaultValue("b")
				.setSaveConsumer(v -> cfg.allyColor = v)
				.build());
		chat.addEntry(eb.startStrField(Text.literal("내 닉네임 수동 지정(비우면 자동)"), cfg.selfNameOverride)
				.setDefaultValue("")
				.setSaveConsumer(v -> cfg.selfNameOverride = v)
				.build());

		// ---- Headcount ----
		ConfigCategory headcount = builder.getOrCreateCategory(Text.translatable("text.betterpe.category.headcount"));
		headcount.addEntry(eb.startBooleanToggle(Text.literal("인원파악 사용"), cfg.headcountEnabled)
				.setDefaultValue(true)
				.setTooltip(Text.literal("조작 설정(Controls)의 'Better PlanetEarth' 항목에서 키를 바꿀 수 있습니다. 기본: Page Down"))
				.setSaveConsumer(v -> cfg.headcountEnabled = v)
				.build());
		headcount.addEntry(eb.startDoubleField(Text.literal("조사 반경(블록)"), cfg.headcountRadius)
				.setDefaultValue(100.0)
				.setMin(5.0).setMax(500.0)
				.setSaveConsumer(v -> cfg.headcountRadius = v)
				.build());
		headcount.addEntry(eb.startBooleanToggle(Text.literal("무소속 인원도 집계"), cfg.headcountIncludeUnaffiliated)
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.headcountIncludeUnaffiliated = v)
				.build());
		headcount.addEntry(eb.startBooleanToggle(Text.literal("국가별 닉네임 목록도 표시"), cfg.headcountShowNames)
				.setDefaultValue(false)
				.setSaveConsumer(v -> cfg.headcountShowNames = v)
				.build());

		// ---- Brewery ----
		ConfigCategory brewery = builder.getOrCreateCategory(Text.translatable("text.betterpe.category.brewery"));
		brewery.addEntry(eb.startBooleanToggle(Text.literal("양조 타이머 사용"), cfg.breweryEnabled)
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.breweryEnabled = v)
				.build());
		brewery.addEntry(eb.startIntSlider(Text.literal("측정 시작 대기시간(초)"), cfg.breweryStartDelaySeconds, 1, 20)
				.setDefaultValue(3)
				.setTooltip(Text.literal("/양조선택 후 가마솥에 시계를 우클릭한 시점부터 실제 카운트다운이 시작되기까지의 대기시간입니다."))
				.setSaveConsumer(v -> cfg.breweryStartDelaySeconds = v)
				.build());

		// ---- Boat auto-move ----
		ConfigCategory boat = builder.getOrCreateCategory(Text.translatable("text.betterpe.category.boat"));
		boat.addEntry(eb.startBooleanToggle(Text.literal("보트 자동이동 사용"), cfg.boatAutoMoveEnabled)
				.setDefaultValue(true)
				.setTooltip(Text.literal(
						"보트에 타고 있는 동안 W키를 자동으로 눌러줍니다 (규정 2조 2항 예외 범위 내: 방향 조작·경로탐색은 하지 않습니다).\n"
								+ "조작 설정(Controls)의 'Better PlanetEarth' 항목에서 토글 키를 바꿀 수 있습니다."))
				.setSaveConsumer(v -> cfg.boatAutoMoveEnabled = v)
				.build());

		// ---- Update checker ----
		ConfigCategory update = builder.getOrCreateCategory(Text.translatable("text.betterpe.category.update"));
		update.addEntry(eb.startBooleanToggle(Text.literal("업데이트 확인 사용"), cfg.updateCheckEnabled)
				.setDefaultValue(true)
				.setTooltip(Text.literal(
						"게임 접속 시 GitHub에 새 버전이 있는지 확인하고, 있으면 채팅에 알림을 띄웁니다.\n"
								+ "알림의 버튼을 누르면 새 jar를 받아두고, 다음에 게임을 재시작할 때 적용됩니다."))
				.setSaveConsumer(v -> cfg.updateCheckEnabled = v)
				.build());

		// ---- Remote status webpage ----
		ConfigCategory remote = builder.getOrCreateCategory(Text.translatable("text.betterpe.category.remote"));
		remote.addEntry(eb.startBooleanToggle(Text.literal("원격 상태 페이지 사용"), cfg.remoteStatusEnabled)
				.setDefaultValue(false)
				.setTooltip(Text.literal(
						"http://better-planetearth.ggm.kr/<닉네임> 에서 온라인/대기열 상태 확인 및 원격 접속종료가 가능해집니다.\n"
								+ "아래 비밀번호를 처음 켤 때 정하면 그 값이 그대로 서버에 등록되고, 그 뒤로는 그 비밀번호로만 인증됩니다."))
				.setSaveConsumer(v -> cfg.remoteStatusEnabled = v)
				.build());
		remote.addEntry(eb.startStrField(Text.literal("페이지 비밀번호"), cfg.remoteStatusPassword)
				.setDefaultValue("")
				.setTooltip(Text.literal("비어 있으면 원격 상태 페이지 기능이 동작하지 않습니다."))
				.setSaveConsumer(v -> cfg.remoteStatusPassword = v)
				.build());
		remote.addEntry(eb.startStrField(Text.literal("서버 주소(고급)"), cfg.remoteStatusServerUrl)
				.setDefaultValue("wss://better-planetearth.ggm.kr/ws")
				.setSaveConsumer(v -> cfg.remoteStatusServerUrl = v)
				.build());

		return builder.build();
	}
}
