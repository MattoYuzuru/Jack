import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import '@fontsource-variable/manrope/index.css'
import '@fontsource-variable/space-grotesk/index.css'
import './styles.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
