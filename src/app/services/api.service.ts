import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = '/api';

  // Dashboard
  getDashboardStats(): Observable<any> {
    return this.http.get(`${this.base}/dashboard/stats`);
  }
  getInstructionsRecentes(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/dashboard/instructions-recentes`);
  }

  // Instructions / Threads
  getInstructions(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/instructions`);
  }
  createInstruction(data: any): Observable<any> {
    return this.http.post(`${this.base}/instructions`, data);
  }

  // Messages
  getMessages(instructionId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/instructions/${instructionId}/messages`);
  }
  sendMessage(instructionId: string, msg: any): Observable<any> {
    return this.http.post(`${this.base}/instructions/${instructionId}/messages`, msg);
  }
  validateMessage(msgId: string): Observable<any> {
    return this.http.patch(`${this.base}/instructions/messages/${msgId}/validate`, {});
  }
  rejectMessage(msgId: string): Observable<any> {
    return this.http.patch(`${this.base}/instructions/messages/${msgId}/reject`, {});
  }

  // Courrier Arrivé
  getCourriersArrive(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/courriers-arrive`);
  }
  getCourrierArrive(id: string): Observable<any> {
    return this.http.get(`${this.base}/courriers-arrive/${id}`);
  }
  updateCourrierArrive(id: string, body: any): Observable<any> {
    return this.http.patch(`${this.base}/courriers-arrive/${id}`, body);
  }

  // Courrier Départ
  getCourriersDepart(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/courriers-depart`);
  }
  getCourrierDepart(id: string): Observable<any> {
    return this.http.get(`${this.base}/courriers-depart/${id}`);
  }
  updateCourrierDepart(id: string, body: any): Observable<any> {
    return this.http.patch(`${this.base}/courriers-depart/${id}`, body);
  }

  // Rendez-vous
  getRendezVous(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/rendez-vous`);
  }
  getRendezVousCalendrier(mois: string): Observable<Record<string, any[]>> {
    return this.http.get<Record<string, any[]>>(`${this.base}/rendez-vous/calendrier`, { params: { mois } });
  }
  getRendezVousById(id: string): Observable<any> {
    return this.http.get(`${this.base}/rendez-vous/${id}`);
  }
  updateRendezVous(id: string, body: any): Observable<any> {
    return this.http.patch(`${this.base}/rendez-vous/${id}`, body);
  }

  // Workflow de validation
  soumettre(instructionId: string, body: any): Observable<any> {
    return this.http.post(`${this.base}/instructions/${instructionId}/soumettre`, body);
  }
  valider(instructionId: string, body: any): Observable<any> {
    return this.http.post(`${this.base}/instructions/${instructionId}/valider`, body);
  }
  rejeter(instructionId: string, body: any): Observable<any> {
    return this.http.post(`${this.base}/instructions/${instructionId}/rejeter`, body);
  }
  getWorkflow(instructionId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/instructions/${instructionId}/workflow`);
  }

  // Fichiers (MinIO)
  uploadFile(file: File): Observable<{ name: string }> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ name: string }>(`${this.base}/files/upload`, form);
  }
  getFileUrl(name: string): string {
    return `${this.base}/files/${encodeURIComponent(name)}`;
  }

  // Recherche globale
  search(q: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/search`, { params: { q } });
  }

  // Audit trail
  getAuditLogs(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/audit`);
  }
  getAuditByEntity(entityType: string, entityId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/audit/${entityType}/${entityId}`);
  }

  // Modèles de courrier départ
  getTemplatesCourrier(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/templates-courrier`);
  }
  createCourrierDepart(data: any): Observable<any> {
    return this.http.post(`${this.base}/courriers-depart`, data);
  }

  // Signature assets
  getSignatureAssets(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/users/me/signature-assets`);
  }
  uploadSignatureAsset(assetType: string, file: File): Observable<any> {
    const form = new FormData();
    form.append('assetType', assetType);
    form.append('file', file);
    return this.http.post(`${this.base}/users/me/signature-assets`, form);
  }
  getSignatureImageUrl(id: string): string {
    return `${this.base}/users/me/signature-assets/${id}/image`;
  }
  getSignatureImageBlob(id: string): Observable<string> {
    return this.http.get(`${this.base}/users/me/signature-assets/${id}/image`, { responseType: 'blob' })
      .pipe(map(blob => URL.createObjectURL(blob)));
  }
  deleteSignatureAsset(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/users/me/signature-assets/${id}`);
  }

  // Annotations
  getAnnotations(pageId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/pages/${encodeURIComponent(pageId)}/annotations`);
  }
  createAnnotation(body: any): Observable<any> {
    return this.http.post(`${this.base}/annotations`, body);
  }
  updateAnnotation(id: string, body: any): Observable<any> {
    return this.http.put(`${this.base}/annotations/${id}`, body);
  }
  deleteAnnotation(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/annotations/${id}`);
  }

  // PDF documents
  getPdfDocuments(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/pdf-documents`);
  }
  uploadPdfDocument(file: File, title?: string): Observable<any> {
    const form = new FormData();
    form.append('file', file);
    if (title) form.append('title', title);
    return this.http.post(`${this.base}/pdf-documents`, form);
  }
  renderPage(docId: string, page: number): string {
    return `${this.base}/pdf-documents/${docId}/pages/${page}/image`;
  }
  finalizePdfDocument(docId: string): Observable<any> {
    return this.http.post(`${this.base}/pdf-documents/${docId}/finalize`, {});
  }
  downloadFinalPdf(docId: string): string {
    return `${this.base}/pdf-documents/${docId}/final`;
  }
  deletePdfDocument(docId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/pdf-documents/${docId}`);
  }

  // Paramètres — Types d'Instructions
  getInstructionTypes(activeOnly = false): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/parametres/instruction-types`, { params: { activeOnly } });
  }
  createInstructionType(body: any): Observable<any> {
    return this.http.post(`${this.base}/parametres/instruction-types`, body);
  }
  updateInstructionType(id: string, body: any): Observable<any> {
    return this.http.put(`${this.base}/parametres/instruction-types/${id}`, body);
  }
  toggleInstructionType(id: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/parametres/instruction-types/${id}/toggle`, {});
  }
  deleteInstructionType(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/parametres/instruction-types/${id}`);
  }

  // Paramètres — Types de Preuves
  getProofTypes(activeOnly = false): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/parametres/proof-types`, { params: { activeOnly } });
  }
  createProofType(body: any): Observable<any> {
    return this.http.post(`${this.base}/parametres/proof-types`, body);
  }
  updateProofType(id: string, body: any): Observable<any> {
    return this.http.put(`${this.base}/parametres/proof-types/${id}`, body);
  }
  toggleProofType(id: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/parametres/proof-types/${id}/toggle`, {});
  }
  deleteProofType(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/parametres/proof-types/${id}`);
  }

  // Users
  getUsers(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/parametres/users`);
  }
  createUser(body: any): Observable<any> {
    return this.http.post(`${this.base}/parametres/users`, body);
  }
  updateUser(id: string, body: any): Observable<any> {
    return this.http.put(`${this.base}/parametres/users/${id}`, body);
  }
  resetUserPassword(id: string, password: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/parametres/users/${id}/reset-password`, { password });
  }
  toggleUser(id: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/parametres/users/${id}/toggle`, {});
  }
  deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/parametres/users/${id}`);
  }

  // Bureau Secrétaire
  getBureauDocuments(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/bureau/documents`);
  }
  uploadBureauDocument(file: File, type: string, titre?: string, destinataire?: string): Observable<any> {
    const form = new FormData();
    form.append('file', file);
    form.append('type', type);
    if (titre) form.append('titre', titre);
    if (destinataire) form.append('destinataire', destinataire);
    return this.http.post(`${this.base}/bureau/documents`, form);
  }
  getBureauPageUrl(docId: string, pageIndex: number): string {
    return `${this.base}/bureau/documents/${docId}/page/${pageIndex}`;
  }
  getBureauPage(docId: string, pageIndex: number): Observable<string> {
    return this.http.get(`${this.base}/bureau/documents/${docId}/page/${pageIndex}`, { responseType: 'blob' })
      .pipe(map(blob => URL.createObjectURL(blob)));
  }
  saveBureauZones(docId: string, zones: any): Observable<any> {
    return this.http.post(`${this.base}/bureau/documents/${docId}/zones`, zones);
  }
  soumettreAuParapheur(docId: string): Observable<any> {
    return this.http.post(`${this.base}/bureau/documents/${docId}/soumettre`, {});
  }
  deleteBureauDocument(docId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/bureau/documents/${docId}`);
  }

  // Parapheur
  getParapheurPending(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/parapheur`);
  }
  getParapheurHistorique(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/parapheur/historique`);
  }
  getNotesDeService(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/parapheur/notes-de-service`);
  }
  soumettreDocument(file: File, title: string, type: string, destinataire?: string): Observable<any> {
    const form = new FormData();
    form.append('file', file);
    form.append('title', title);
    form.append('parapheurType', type);
    if (destinataire) form.append('destinataire', destinataire);
    return this.http.post(`${this.base}/parapheur/soumettre`, form);
  }
  signerDocument(docId: string): Observable<any> {
    return this.http.post(`${this.base}/parapheur/${docId}/signer`, {});
  }
  rejeterDocument(docId: string, comment: string): Observable<any> {
    return this.http.post(`${this.base}/parapheur/${docId}/rejeter`, { comment });
  }

  // Collaborateurs
  getCollaborateurs(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/collaborateurs`);
  }
  addCollaborateur(data: any): Observable<any> {
    return this.http.post(`${this.base}/collaborateurs`, data);
  }
  deleteCollaborateur(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/collaborateurs/${id}`);
  }
}
