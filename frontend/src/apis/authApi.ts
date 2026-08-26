import http from "./http";

export interface AuthResponse {
  token: string;
  username: string;
  role: string;
}

export const register = async (username: string, password: string, inviteCode: string) => {
  const { data } = await http.post<AuthResponse>("/api/auth/register", { username, password, inviteCode });
  return data;
};

export const login = async (username: string, password: string) => {
  const { data } = await http.post<AuthResponse>("/api/auth/login", { username, password });
  return data;
};

export const guestLogin = async () => {
  const { data } = await http.post<AuthResponse>("/api/auth/guest");
  return data;
};
