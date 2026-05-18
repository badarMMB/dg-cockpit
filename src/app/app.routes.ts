import { Routes } from '@angular/router';
import { AppLayoutComponent } from './layout/app-layout/app-layout.component';
import { LoginComponent } from './pages/login/login.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { SignatureComponent } from './pages/signature/signature.component';
import { ChatComponent } from './pages/chat/chat.component';
import { SettingsComponent } from './pages/settings/settings.component';
import { EditorComponent } from './pages/editor/editor.component';
import { InboxListComponent } from './pages/inbox/inbox-list/inbox-list.component';
import { InboxDetailComponent } from './pages/inbox/inbox-detail/inbox-detail.component';
import { OutboxListComponent } from './pages/outbox/outbox-list/outbox-list.component';
import { OutboxDetailComponent } from './pages/outbox/outbox-detail/outbox-detail.component';
import { AppointmentListComponent } from './pages/appointments/appointment-list/appointment-list.component';
import { AppointmentDetailComponent } from './pages/appointments/appointment-detail/appointment-detail.component';
import { SignatureAssetsComponent } from './pages/signature-assets/signature-assets.component';
import { PdfDocumentsComponent } from './pages/pdf-documents/pdf-documents.component';
import { PdfViewerComponent } from './pages/pdf-viewer/pdf-viewer.component';
import { ParametresComponent } from './pages/parametres/parametres.component';
import { BureauComponent } from './pages/bureau/bureau.component';
import { BureauPlacementComponent } from './pages/bureau/bureau-placement.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: AppLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard',         component: DashboardComponent },
      { path: 'chat',              component: ChatComponent },
      { path: 'editor',            component: EditorComponent },
      { path: 'signature',         component: SignatureComponent },
      { path: 'signature-assets',  component: SignatureAssetsComponent },
      { path: 'pdf-documents',     component: PdfDocumentsComponent },
      { path: 'pdf-viewer/:id',    component: PdfViewerComponent },
      { path: 'settings',          component: SettingsComponent },
      { path: 'inbox',             component: InboxListComponent },
      { path: 'inbox/:id',         component: InboxDetailComponent },
      { path: 'outbox',            component: OutboxListComponent },
      { path: 'outbox/:id',        component: OutboxDetailComponent },
      { path: 'appointments',      component: AppointmentListComponent },
      { path: 'appointments/:id',  component: AppointmentDetailComponent },
      { path: 'parametres',         component: ParametresComponent },
      { path: 'bureau',             component: BureauComponent },
      { path: 'bureau-placement/:id', component: BureauPlacementComponent },
    ]
  }
];
