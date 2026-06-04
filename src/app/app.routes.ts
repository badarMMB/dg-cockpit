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
import { ClasseursListComponent } from './pages/classeurs/classeurs-list.component';
import { ClasseurDetailComponent } from './pages/classeurs/classeur-detail.component';
import { NotesDeServiceComponent } from './pages/notes/notes-de-service.component';
import { authGuard } from './guards/auth.guard';
import { bureauGuard } from './guards/bureau.guard';
import { permissionGuard } from './guards/permission.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: AppLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard',            component: DashboardComponent },
      { path: 'chat',                 component: ChatComponent },
      { path: 'editor',               component: EditorComponent },
      { path: 'signature',            component: SignatureComponent,       canActivate: [authGuard] },
      { path: 'signature-assets',     component: SignatureAssetsComponent },
      { path: 'pdf-documents',        component: PdfDocumentsComponent,    canActivate: [permissionGuard], data: { anyPermissions: ['CAN_VIEW_ALL'] } },
      { path: 'pdf-viewer/:id',       component: PdfViewerComponent },
      { path: 'settings',             component: SettingsComponent },
      { path: 'inbox',                component: InboxListComponent,       canActivate: [permissionGuard], data: { anyPermissions: ['HAS_BUREAU', 'CAN_VIEW_ALL'] } },
      { path: 'inbox/:id',            component: InboxDetailComponent,     canActivate: [permissionGuard], data: { anyPermissions: ['HAS_BUREAU', 'CAN_VIEW_ALL'] } },
      { path: 'outbox',               component: OutboxListComponent,      canActivate: [permissionGuard], data: { anyPermissions: ['HAS_BUREAU', 'CAN_VIEW_ALL'] } },
      { path: 'outbox/:id',           component: OutboxDetailComponent,    canActivate: [permissionGuard], data: { anyPermissions: ['HAS_BUREAU', 'CAN_VIEW_ALL'] } },
      { path: 'appointments',         component: AppointmentListComponent, canActivate: [permissionGuard], data: { anyPermissions: ['HAS_BUREAU', 'CAN_VIEW_ALL'] } },
      { path: 'appointments/:id',     component: AppointmentDetailComponent, canActivate: [permissionGuard], data: { anyPermissions: ['HAS_BUREAU', 'CAN_VIEW_ALL'] } },
      { path: 'parametres',           component: ParametresComponent,      canActivate: [permissionGuard], data: { anyPermissions: ['CAN_MANAGE_USERS', 'CAN_MANAGE_TYPES'] } },
      { path: 'bureau',               component: BureauComponent,          canActivate: [bureauGuard] },
      { path: 'bureau-placement/:id', component: BureauPlacementComponent, canActivate: [bureauGuard] },
      { path: 'classeurs',            component: ClasseursListComponent,   canActivate: [permissionGuard], data: { anyPermissions: ['HAS_BUREAU', 'CAN_VIEW_ALL'] } },
      { path: 'classeurs/:id',        component: ClasseurDetailComponent,  canActivate: [permissionGuard], data: { anyPermissions: ['HAS_BUREAU', 'CAN_VIEW_ALL'] } },
      { path: 'notes-de-service',     component: NotesDeServiceComponent },
    ]
  }
];
