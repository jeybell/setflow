import { createRouter, createWebHistory } from "vue-router";
import HomeView from "../views/HomeView.vue";
import SongCreateView from "../views/SongCreateView.vue";
import SongDetailView from "../views/SongDetailView.vue";
import SongListView from "../views/SongListView.vue";
import BulkUploadView from "../views/BulkUploadView.vue";
import SetlistListView from "../views/SetlistListView.vue";
import SetlistDetailView from "../views/SetlistDetailView.vue";
import SetlistPresentView from "../views/SetlistPresentView.vue";
import FeatureRequestView from "../views/FeatureRequestView.vue";
import ShareView from "../views/ShareView.vue";
import LoginView from "../views/LoginView.vue";
import RegisterView from "../views/RegisterView.vue";
import AdminView from "../views/AdminView.vue";
import { AUTH_TOKEN_KEY, AUTH_ROLE_KEY, PUBLIC_PATH_PREFIXES } from "../apis/http";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      name: "home",
      component: HomeView,
    },
    {
      path: "/songs",
      name: "songs",
      component: SongListView,
    },
    {
      path: "/songs/new",
      name: "song-create",
      component: SongCreateView,
    },
    {
      path: "/songs/bulk",
      name: "song-bulk-upload",
      component: BulkUploadView,
    },
    {
      path: "/songs/:songId",
      name: "song-detail",
      component: SongDetailView,
      props: (route) => ({
        songId: Number(route.params.songId),
      }),
    },
    {
      path: "/setlists",
      name: "setlists",
      component: SetlistListView,
    },
    {
      path: "/setlists/:setlistId",
      name: "setlist-detail",
      component: SetlistDetailView,
      props: (route) => ({ setlistId: Number(route.params.setlistId) }),
    },
    {
      path: "/setlists/:setlistId/present",
      name: "setlist-present",
      component: SetlistPresentView,
      props: (route) => ({ setlistId: Number(route.params.setlistId) }),
    },
    {
      path: "/feature-requests",
      name: "feature-requests",
      component: FeatureRequestView,
    },
    {
      path: "/share/:token",
      name: "share",
      component: ShareView,
    },
    {
      path: "/admin",
      name: "admin",
      component: AdminView,
      meta: { requiresAdmin: true },
    },
    {
      path: "/login",
      name: "login",
      component: LoginView,
    },
    {
      path: "/register",
      name: "register",
      component: RegisterView,
    },
  ],
});

router.beforeEach((to) => {
  const isPublic = PUBLIC_PATH_PREFIXES.some((prefix) => to.path.startsWith(prefix));
  const isAuthenticated = !!localStorage.getItem(AUTH_TOKEN_KEY);

  if (!isPublic && !isAuthenticated) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }
  if ((to.name === "login" || to.name === "register") && isAuthenticated) {
    return { path: "/" };
  }
  // 관리자 전용 라우트: ADMIN 역할이 아니면 홈으로. (서버도 /api/admin/** 을 hasRole('ADMIN') 으로 보호)
  if (to.meta.requiresAdmin && localStorage.getItem(AUTH_ROLE_KEY) !== "ADMIN") {
    return { path: "/" };
  }
});

export default router;
