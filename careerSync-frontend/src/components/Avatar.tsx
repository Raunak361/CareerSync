import { hueOf } from '../utils';

export default function Avatar({ name, size = 44 }: { name?: string; size?: number }) {
  const hue = hueOf(name);
  return (
    <div
      className="avatar"
      style={{
        width: size, height: size, fontSize: size * 0.4,
        background: `hsl(${hue} 80% 94%)`, color: `hsl(${hue} 60% 32%)`,
      }}
      aria-hidden="true"
    >
      {name?.slice(0, 1).toUpperCase() || '?'}
    </div>
  );
}
