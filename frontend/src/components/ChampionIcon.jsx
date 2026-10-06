const SPRITE_CELL = 48 // Data Dragon 스프라이트 시트의 썸네일 한 칸 크기(px)

/**
 * [역할] 챔피언 초상화 아이콘. 가벼운 48x48 스프라이트 썸네일을 원하는 크기로 축소해 표시한다.
 *
 * [Data Flow]
 *   GET /api/v1/champions 응답의 champion { spriteUrl, spriteX, spriteY, iconUrl, cost }
 *     --> 스프라이트 시트에서 (spriteX, spriteY) 위치의 48x48 영역만 잘라 표시
 *     --> 스프라이트 정보가 없으면 원본 이미지(iconUrl, 1024x512 스플래시)를 잘라 표시
 *   테두리 색은 코스트(1~5)에 따라 달라진다.
 */
export default function ChampionIcon({ champion, size = 28 }) {
  const boxStyle = { width: size, height: size }

  if (!champion) {
    return <span className="champion-icon is-empty" style={boxStyle} aria-hidden="true" />
  }

  const costClass = `cost-${champion.cost}`
  if (champion.spriteUrl) {
    return (
      <span className={`champion-icon ${costClass}`} style={boxStyle} aria-hidden="true">
        <span
          className="champion-icon-sprite"
          style={{
            backgroundImage: `url("${champion.spriteUrl}")`,
            backgroundPosition: `-${champion.spriteX}px -${champion.spriteY}px`,
            transform: `scale(${size / SPRITE_CELL})`,
          }}
        />
      </span>
    )
  }

  return (
    <span className={`champion-icon ${costClass}`} style={boxStyle} aria-hidden="true">
      {champion.iconUrl && <img src={champion.iconUrl} alt="" loading="lazy" />}
    </span>
  )
}
