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
import { roleGuard } from './guards/role.guard';
import { bureauGuard } from './guards/bureau.guard';

const DG         = ['DG'];
const DG_SEC     = ['DG', 'SECRETAIRE'];
const DG_SEC_SUB = ['DG', 'SECRETAIRE', 'SUBORDONNE'];
const ALL        = ['DG', 'SECRETAIRE', 'SUBORDONNE', 'ADMIN_IT'];
const DG_ADMIN   = ['DG', 'ADMIN_IT'];
const SEC        = ['SECRETAIRE'];

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: AppLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard',            component: DashboardComponent },
      { path: 'chat',                 component: ChatComponent,            canActivate: [roleGuard], data: { roles: DG_SEC_SUB } },
      { path: 'editor',               component: EditorComponent },
      { path: 'signature',            component: SignatureComponent,       canActivate: [authGuard] },
      { path: 'signature-assets',     component: SignatureAssetsComponent, canActivate: [roleGuard], data: { roles: ALL } },
      { path: 'pdf-documents',        component: PdfDocumentsComponent,    canActivate: [roleGuard], data: { roles: DG } },
      { path: 'pdf-viewer/:id',       component: PdfViewerComponent,       canActivate: [roleGuard], data: { roles: DG_SEC_SUB } },
      { path: 'settings',             component: SettingsComponent },
      { path: 'inbox',                component: InboxListComponent,       canActivate: [roleGuard], data: { roles: DG_SEC } },
      { path: 'inbox/:id',            component: InboxDetailComponent,     canActivate: [roleGuard], data: { roles: DG_SEC } },
      { path: 'outbox',               component: OutboxListComponent,      canActivate: [roleGuard], data: { roles: DG_SEC } },
      { path: 'outbox/:id',           component: OutboxDetailComponent,    canActivate: [roleGuard], data: { roles: DG_SEC } },
      { path: 'appointments',         component: AppointmentListComponent, canActivate: [roleGuard], data: { roles: DG_SEC } },
      { path: 'appointments/:id',     component: AppointmentDetailComponent, canActivate: [roleGuard], data: { roles: DG_SEC } },
      { path: 'parametres',           component: ParametresComponent,      canActivate: [roleGuard], data: { roles: DG_ADMIN } },
      { path: 'bureau',               component: BureauComponent,          canActivate: [bureauGuard] },
      { path: 'bureau-placement/:id', component: BureauPlacementComponent, canActivate: [bureauGuard] },
      { path: 'classeurs',            component: ClasseursListComponent,   canActivate: [roleGuard], data: { roles: DG_SEC } },
      { path: 'classeurs/:id',        component: ClasseurDetailComponent,  canActivate: [roleGuard], data: { roles: DG_SEC } },
      { path: 'notes-de-service',     component: NotesDeServiceComponent,  canActivate: [roleGuard], data: { roles: DG_SEC_SUB } },
    ]
  }
];
