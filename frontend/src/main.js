import Vue from 'vue';
import EasyUI from 'vx-easyui';
import locale from 'vx-easyui/dist/locale/easyui-lang-en';
import 'vx-easyui/dist/themes/default/easyui.css';
import 'vx-easyui/dist/themes/icon.css';
import 'vx-easyui/dist/themes/vue.css';
import './styles/dashboard.css';
import App from './App.vue';

Vue.config.productionTip = false;
Vue.use(EasyUI, { locale });

new Vue({
  render: (h) => h(App)
}).$mount('#app');
