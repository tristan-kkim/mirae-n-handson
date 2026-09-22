import type { Unit } from '../api/types';

interface UnitListProps {
  units: Unit[];
  selectedCode: string | null;
  onSelect: (code: string) => void;
}

/** 단원 목록. 클릭하면 onSelect(code) 를 부른다. */
export function UnitList({ units, selectedCode, onSelect }: UnitListProps) {
  if (units.length === 0) {
    return <p className="hint">등록된 단원이 없습니다</p>;
  }

  return (
    <ul className="unit-list" aria-label="단원 목록">
      {units.map((unit) => {
        const selected = unit.code === selectedCode;
        return (
          <li key={unit.id}>
            <button
              type="button"
              className={selected ? 'unit-list__item unit-list__item--selected' : 'unit-list__item'}
              aria-pressed={selected}
              onClick={() => onSelect(unit.code)}
            >
              <span className="unit-list__code">{unit.code}</span>
              <span className="unit-list__name">{unit.name}</span>
              <span className="unit-list__grade">{unit.grade}학년</span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
