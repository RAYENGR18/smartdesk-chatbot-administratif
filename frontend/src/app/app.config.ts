import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    // Client HTTP vers le backend Spring Boot ; le token JWT est ajouté automatiquement
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimations()
  ]
};
