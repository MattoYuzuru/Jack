<script setup lang="ts">
defineProps<{
  id: 'viewer' | 'converter' | 'compressor' | 'pdf' | 'editor' | 'devtools'
}>()
</script>

<template>
  <svg v-if="id === 'viewer'" viewBox="0 0 280 190" class="tool-icon">
    <defs>
      <linearGradient id="viewerOrb" x1="0%" x2="100%" y1="0%" y2="100%">
        <stop offset="0%" stop-color="#fff4e5" />
        <stop offset="100%" stop-color="#ffb06f" />
      </linearGradient>
    </defs>
    <ellipse cx="140" cy="96" rx="94" ry="52" fill="none" stroke="#1d5c55" stroke-width="14" />
    <circle cx="140" cy="96" r="28" fill="#1d5c55" />
    <circle cx="150" cy="88" r="12" fill="url(#viewerOrb)" />
    <path
      d="M56 96c17-28 48-49 84-49s67 21 84 49"
      fill="none"
      stroke="#f38a55"
      stroke-linecap="round"
      stroke-width="9"
    />
    <path
      d="M56 96c17 28 48 49 84 49s67-21 84-49"
      fill="none"
      stroke="#f8d7ac"
      stroke-linecap="round"
      stroke-width="9"
    />
    <path
      d="M82 52c6-8 18-14 30-16"
      fill="none"
      stroke="#ffffff"
      stroke-linecap="round"
      stroke-width="7"
    />
  </svg>

  <svg v-else-if="id === 'converter'" viewBox="0 0 280 190" class="tool-icon">
    <rect
      x="97"
      y="44"
      width="86"
      height="102"
      rx="24"
      fill="#efe5d7"
      stroke="#1d5c55"
      stroke-width="10"
    />
    <path d="M111 82h58" stroke="#f38a55" stroke-linecap="round" stroke-width="10" />
    <path d="M111 108h42" stroke="#f8d7ac" stroke-linecap="round" stroke-width="10" />
    <path
      d="M69 90c0-28 22-50 50-50"
      fill="none"
      stroke="#1d5c55"
      stroke-linecap="round"
      stroke-width="12"
    />
    <path
      d="M105 28l22 12-22 12"
      fill="none"
      stroke="#1d5c55"
      stroke-linecap="round"
      stroke-linejoin="round"
      stroke-width="12"
    />
    <path
      d="M211 100c0 28-22 50-50 50"
      fill="none"
      stroke="#f38a55"
      stroke-linecap="round"
      stroke-width="12"
    />
    <path
      d="M175 162l-22-12 22-12"
      fill="none"
      stroke="#f38a55"
      stroke-linecap="round"
      stroke-linejoin="round"
      stroke-width="12"
    />
  </svg>

  <svg v-else-if="id === 'compressor'" viewBox="0 0 280 190" class="tool-icon">
    <rect
      x="78"
      y="42"
      width="124"
      height="104"
      rx="28"
      fill="#efe5d7"
      stroke="#1d5c55"
      stroke-width="10"
    />
    <path d="M112 64v58" stroke="#f38a55" stroke-linecap="round" stroke-width="12" />
    <path d="M140 64v58" stroke="#1d5c55" stroke-linecap="round" stroke-width="12" />
    <path d="M168 64v58" stroke="#f8d7ac" stroke-linecap="round" stroke-width="12" />
    <path
      d="M97 32l18 18-18 18"
      fill="none"
      stroke="#ffffff"
      stroke-linecap="round"
      stroke-linejoin="round"
      stroke-width="10"
    />
    <path
      d="M183 158l-18-18 18-18"
      fill="none"
      stroke="#ffffff"
      stroke-linecap="round"
      stroke-linejoin="round"
      stroke-width="10"
    />
    <path d="M50 94h42" stroke="#1d5c55" stroke-linecap="round" stroke-width="12" />
    <path d="M188 94h42" stroke="#f38a55" stroke-linecap="round" stroke-width="12" />
  </svg>

  <svg v-else-if="id === 'pdf'" viewBox="0 0 280 190" class="tool-icon">
    <path
      d="M90 42h72l28 28v78a20 20 0 0 1-20 20H90a20 20 0 0 1-20-20V62a20 20 0 0 1 20-20Z"
      fill="#efe5d7"
      stroke="#1d5c55"
      stroke-width="10"
    />
    <path
      d="M162 42v28h28"
      fill="none"
      stroke="#1d5c55"
      stroke-linejoin="round"
      stroke-width="10"
    />
    <path d="M102 98h56" stroke="#f38a55" stroke-linecap="round" stroke-width="10" />
    <path d="M102 122h42" stroke="#f8d7ac" stroke-linecap="round" stroke-width="10" />
    <circle cx="197" cy="128" r="24" fill="#ffb06f" stroke="#1d5c55" stroke-width="10" />
    <path d="M197 112v32" stroke="#fffaf0" stroke-linecap="round" stroke-width="10" />
    <path d="M181 128h32" stroke="#fffaf0" stroke-linecap="round" stroke-width="10" />
  </svg>

  <svg v-else-if="id === 'editor'" viewBox="0 0 280 190" class="tool-icon">
    <rect
      x="58"
      y="42"
      width="164"
      height="108"
      rx="28"
      fill="#efe5d7"
      stroke="#1d5c55"
      stroke-width="10"
    />
    <path
      d="M92 74l-20 20 20 20"
      fill="none"
      stroke="#1d5c55"
      stroke-linecap="round"
      stroke-linejoin="round"
      stroke-width="12"
    />
    <path
      d="M188 74l20 20-20 20"
      fill="none"
      stroke="#f38a55"
      stroke-linecap="round"
      stroke-linejoin="round"
      stroke-width="12"
    />
    <path d="M128 64l-14 60" stroke="#f8d7ac" stroke-linecap="round" stroke-width="12" />
    <rect x="146" y="72" width="24" height="46" rx="10" fill="#ffb06f" />
    <path d="M146 126h60" stroke="#ffffff" stroke-linecap="round" stroke-width="10" />
  </svg>

  <svg v-else viewBox="0 0 280 190" class="tool-icon">
    <rect
      x="52"
      y="46"
      width="176"
      height="98"
      rx="28"
      fill="#efe5d7"
      stroke="#1d5c55"
      stroke-width="10"
    />
    <path d="M88 82h20" stroke="#f38a55" stroke-linecap="round" stroke-width="12" />
    <path d="M122 82h18" stroke="#1d5c55" stroke-linecap="round" stroke-width="12" />
    <path d="M88 108h52" stroke="#f8d7ac" stroke-linecap="round" stroke-width="12" />
    <circle cx="182" cy="82" r="16" fill="none" stroke="#1d5c55" stroke-width="10" />
    <circle cx="208" cy="118" r="12" fill="#ffb06f" />
    <path d="M182 98v20" stroke="#1d5c55" stroke-linecap="round" stroke-width="10" />
    <path d="M194 91l7 7" stroke="#ffffff" stroke-linecap="round" stroke-width="8" />
    <path d="M170 128h22" stroke="#1d5c55" stroke-linecap="round" stroke-width="10" />
  </svg>
</template>

<style scoped>
.tool-icon {
  width: min(100%, 240px);
  height: auto;
  filter: drop-shadow(0 18px 24px rgba(17, 55, 51, 0.08));
}
</style>
