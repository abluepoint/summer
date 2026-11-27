import React, { useRef, useState } from 'react';
import styles from './style.module.less';

const minWidth = 200;
const maxWidth = 480;
const defaultWidth = 320;

function clampWidth(width: number) {
  return Math.min(maxWidth, Math.max(minWidth, Math.round(width)));
}

type Props = {
  id: string;
  label: string;
  children: React.ReactNode;
};

export default function ResizableTreePanel({ id, label, children }: Props) {
  const [width, setWidth] = useState(defaultWidth);
  const drag =
    useRef<{ pointerId: number; startX: number; startWidth: number }>();
  return (
    <aside
      id={id}
      className={styles.panel}
      aria-label={label}
      style={{ width }}
    >
      <div
        className={styles.divider}
        role="separator"
        tabIndex={0}
        aria-label={`调整${label}宽度`}
        title="拖动或使用左右方向键调整宽度"
        aria-orientation="vertical"
        aria-controls={id}
        aria-valuemin={minWidth}
        aria-valuemax={maxWidth}
        aria-valuenow={width}
        aria-valuetext={`${width} 像素`}
        onPointerDown={(event) => {
          if (!event.isPrimary || event.button !== 0) return;
          event.currentTarget.setPointerCapture(event.pointerId);
          drag.current = {
            pointerId: event.pointerId,
            startX: event.clientX,
            startWidth: width,
          };
        }}
        onPointerMove={(event) => {
          const active = drag.current;
          if (active && active.pointerId === event.pointerId) {
            setWidth(
              clampWidth(active.startWidth + event.clientX - active.startX)
            );
          }
        }}
        onLostPointerCapture={() => {
          drag.current = undefined;
        }}
        onKeyDown={(event) => {
          const next = {
            ArrowLeft: width - 10,
            ArrowRight: width + 10,
            Home: minWidth,
            End: maxWidth,
          }[event.key];
          if (next === undefined) return;
          event.preventDefault();
          setWidth(clampWidth(next));
        }}
      />
      {children}
    </aside>
  );
}
