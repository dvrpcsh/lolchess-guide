/**
 * [역할] 아이템 조합 규칙 엔진. 백엔드가 내려준 조합법으로 [재료 A + 재료 B = 완성 아이템] 매핑을 만든다.
 *
 * [Data Flow]
 *   GET /api/v1/items 응답의 recipes [{ componentA, componentB, result }] (백엔드 ItemRecipeBook, 한글 이름)
 *     --> createRecipeBook() --> recipeBook
 *     --> boardState.equipItem()     : 기물 슬롯에서 재료 2개를 완성 아이템으로 자동 합성
 *     --> boardState.unequipItem() 등 : 완성 아이템 해제 시 재료 2개로 분해해 인벤토리에 반환
 *     --> CompletedItemCodex          : 완성 아이템 도감 목록 / 조합식 표시
 *
 * 예) combine('B.F. 대검', '곡궁') === '거인 학살자', componentsOf('무한의 대검') === ['B.F. 대검', '연습용 장갑']
 */
export function createRecipeBook(recipes = []) {
  const resultByPair = new Map()
  const componentsByResult = new Map()
  recipes.forEach(({ componentA, componentB, result }) => {
    resultByPair.set(pairKey(componentA, componentB), result)
    componentsByResult.set(result, [componentA, componentB])
  })

  return {
    /** 재료 두 개의 조합 결과 (조합법이 없으면 null). 순서는 상관없다. */
    combine: (a, b) => resultByPair.get(pairKey(a, b)) ?? null,
    /** 완성 아이템의 재료 2개 (완성 아이템이 아니면 null) */
    componentsOf: (name) => componentsByResult.get(name) ?? null,
    /** 조합표에 있는 완성 아이템인지 */
    isCompleted: (name) => componentsByResult.has(name),
    /** 아이템 목록을 재료 단위로 펼친다 (완성 아이템은 재료 2개로, 재료는 그대로). 인벤토리 반환용 */
    toComponents: (names) => names.flatMap((name) => componentsByResult.get(name) ?? [name]),
    /** 조합표의 모든 완성 아이템 [{ name, components: [a, b] }] */
    completedItems: [...componentsByResult].map(([name, components]) => ({ name, components })),
  }
}

export const EMPTY_RECIPE_BOOK = createRecipeBook()

function pairKey(a, b) {
  return [a, b].sort().join(' + ')
}
