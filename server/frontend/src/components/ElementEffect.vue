<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({ subject: { type: Object, default: null } })
const effects = {
  Feu: 'fire', Eau: 'water', Vie: 'life', Magie: 'magic', Tech: 'tech',
  Terre: 'earth', Air: 'air', 'Mort-Vivant': 'undead',
  Lumière: 'light', Ténèbres: 'dark', Kaos: 'kaos'
}
const kind = computed(() => effects[props.subject?.element])
const active = ref(false)
const generation = ref(0)
let timeout
// Restart even when two successive characters share the same element.
watch(() => props.subject, subject => {
  clearTimeout(timeout)
  active.value = Boolean(subject && kind.value)
  generation.value++
  if (active.value) timeout = setTimeout(() => { active.value = false }, 4800)
}, { immediate: true })
onBeforeUnmount(() => clearTimeout(timeout))

const particles = Array.from({ length: 24 }, (_, i) => ({
  '--x': `${(i * 37 + 3) % 100}%`,
  '--size': `${18 + (i * 17) % 48}px`,
  '--delay': `${(i % 8) * 0.13}s`,
  '--duration': `${2.2 + (i % 5) * 0.24}s`,
  '--drift': `${((i * 43) % 180) - 90}px`,
  '--spin': `${i % 2 ? 150 : -170}deg`,
  '--depth': `${(i % 3) * 65}px`
}))
</script>

<template>
  <Teleport to="body">
    <div v-if="active" :key="generation" class="element-effect" :class="`effect-${kind}`"
         aria-hidden="true" :data-element="subject.element">
      <div class="atmosphere"></div>
      <div class="surfaces"><i v-for="n in 3" :key="n" :style="{ '--layer': n }"></i></div>
      <div class="particles">
        <span v-for="(style, i) in particles" :key="i" :style="style"><i></i></span>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
/* The effect floats over the screen edges, leaving controls clickable and text clear. */
.element-effect {
  --tint: #ff732a; --shine: #fff0ad;
  position: fixed; inset: 0; z-index: 21; overflow: hidden;
  pointer-events: none; perspective: 800px;
  animation: appearance 4.8s ease both;
  mask-image: linear-gradient(to top, #000 0%, #0009 22%, #0000 55%);
}
.element-effect * { pointer-events: none; }
.atmosphere {
  position: absolute; inset: 35% -10% -25%;
  background: radial-gradient(ellipse at 50% 100%, color-mix(in srgb, var(--tint) 45%, transparent), transparent 65%);
}
.particles { position: absolute; inset: 0; perspective: 800px; }
.particles span {
  position: absolute; left: var(--x); bottom: -90px;
  width: var(--size); height: var(--size);
  animation: ascend var(--duration) var(--delay) ease-out both;
}
.particles i {
  display: block; width: 100%; height: 100%;
  background: radial-gradient(circle at 30% 25%, var(--shine), var(--tint) 45%, #14151c 100%);
  box-shadow: inset -5px -6px 12px #0005, 0 0 18px color-mix(in srgb, var(--tint) 45%, transparent);
  animation: tumble var(--duration) var(--delay) ease-in-out both;
}
.surfaces { position: absolute; inset: 0; perspective: 600px; }
.surfaces i {
  position: absolute; left: -20%; bottom: -180px; width: 140%; height: 260px;
  border-radius: 45% 55% 0 0;
  background: linear-gradient(180deg, var(--shine), var(--tint) 8%, transparent 80%);
  opacity: .25;
  animation: swell 3.8s calc(var(--layer) * .18s) ease-in-out both;
}
.effect-fire .particles i {
  border-radius: 85% 12% 65% 40%;
  background: radial-gradient(ellipse at 35% 85%, #fff6bf, #ffcc45 25%, #ff6923 55%, #b7210600 80%);
  box-shadow: 0 0 22px #ff782b44;
  animation-name: flicker;
  transform-origin: center bottom;
}
.effect-fire .particles span:nth-child(3n) { width: 6px; height: 12px; }
.effect-fire .particles span:not(:nth-child(3n)) { height: calc(var(--size) * 2.5); }
.effect-fire .surfaces { display: none; }
.effect-water { --tint: #168fe8; --shine: #c4fbff; }
.effect-water .particles i {
  border-radius: 50%; background: radial-gradient(circle at 30% 25%, #edffffaa, #229bed22 45%, #b1f7ff99);
  border: 1px solid #c2f6ffa0;
}
.effect-water .surfaces i { border-top: 3px solid #c6f8ffb0; box-shadow: 0 -10px 24px #43bbff44; }
.effect-life { --tint: #5fba40; --shine: #d6f88f; }
.effect-life .particles i { border-radius: 0 80% 0 80%; border-left: 2px solid #e0ffa966; }
.effect-earth { --tint: #ac763f; --shine: #e9c694; }
.effect-earth .particles i { clip-path: polygon(22% 0, 80% 8%, 100% 60%, 65% 100%, 10% 80%, 0 25%); }
.effect-magic { --tint: #aa6cff; --shine: #f6d8ff; }
.effect-magic .particles i { clip-path: polygon(50% 0, 65% 36%, 100% 50%, 65% 64%, 50% 100%, 35% 64%, 0 50%, 35% 36%); }
.effect-tech { --tint: #e8a527; --shine: #fff2a8; }
.effect-tech .particles i {
  border: 5px dashed var(--tint); border-radius: 50%;
  background: radial-gradient(circle, transparent 23%, var(--shine) 25%, #ac671d 42%, transparent 44%);
}
.effect-air { --tint: #b2dcf0; --shine: #ffffff; }
.effect-air .particles i {
  width: 180%; height: 45%; border-radius: 50%; background: none;
  border-top: 3px solid #e1f8ffbb; box-shadow: 0 -5px 10px #b9eaff22;
}
.effect-undead { --tint: #58c6a7; --shine: #d0fff2; }
.effect-undead .particles i { border-radius: 50% 50% 20% 70%; filter: blur(3px); opacity: .65; }
.effect-light { --tint: #ffe4a0; --shine: #ffffff; }
.effect-light .particles i { clip-path: polygon(45% 0, 55% 0, 58% 42%, 100% 48%, 100% 52%, 58% 58%, 55% 100%, 45% 100%, 42% 58%, 0 52%, 0 48%, 42% 42%); }
.effect-dark { --tint: #7953b6; --shine: #c6a8e9; }
.effect-dark .particles i { border-radius: 50%; background: radial-gradient(circle at 35% 30%, #9977c6, #231332 50%, transparent 72%); filter: blur(5px); }
.effect-kaos { --tint: #d14669; --shine: #e9b7ff; }
.effect-kaos .particles i { clip-path: polygon(60% 0, 20% 55%, 48% 55%, 35% 100%, 90% 35%, 60% 35%); }
.effect-life .surfaces, .effect-earth .surfaces, .effect-tech .surfaces,
.effect-magic .surfaces, .effect-light .surfaces, .effect-kaos .surfaces { display: none; }
@keyframes appearance { 0% { opacity: 0; } 10%, 65% { opacity: 1; } 100% { opacity: 0; } }
@keyframes ascend {
  0% { opacity: 0; transform: translate3d(0, 0, var(--depth)); }
  18% { opacity: .85; }
  100% { opacity: 0; transform: translate3d(var(--drift), -65vh, var(--depth)); }
}
@keyframes flicker {
  0%, 100% { transform: skewX(-8deg) scaleX(.8); }
  35% { transform: skewX(12deg) scaleX(1.1); }
  70% { transform: skewX(-12deg) scaleX(.85); }
}
@keyframes tumble { to { transform: rotateX(35deg) rotateY(30deg) rotateZ(var(--spin)); } }
@keyframes swell {
  0% { transform: translate3d(-3%, 120px, 0) rotate(-3deg); }
  40% { transform: translate3d(2%, -65px, 40px) rotate(2deg); }
  100% { transform: translate3d(-2%, 100px, 0) rotate(-2deg); }
}
@media (max-width: 600px) {
  .particles span:nth-child(2n) { display: none; }
  .element-effect { mask-image: linear-gradient(to top, #000b, transparent 35%); }
  .surfaces i { height: 200px; }
}
@media (prefers-reduced-motion: reduce) {
  .element-effect { display: none; }
  .element-effect, .element-effect * { animation: none; }
}
</style>
