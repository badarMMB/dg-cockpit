import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';


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
  cloturerInstruction(instructionId: string): Observable<any> {
    return this.http.post(`${this.base}/instructions/${instructionId}/cloturer`, {});
  }
  getInstructionsPendingForTypeDoc(typeDocId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/instructions/pending-for-document-type/${typeDocId}`);
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

  // Fichiers (MinIO)
  uploadFile(file: File): Observable<{ name: string; url?: string }> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ name: string; url?: string }>(`${this.base}/files/upload`, form);
  }
  getFileUrl(name: string): string {
    return `${this.base}/files/${encodeURIComponent(name)}`;
  }

  downloadFileAsBlob(name: string): Observable<Blob> {
    return this.http.get(`${this.base}/files/${encodeURIComponent(name)}`, { responseType: 'blob' });
  }

  getFileInfo(name: string): Observable<{ pageCount: number; name: string }> {
    return this.http.get<{ pageCount: number; name: string }>(`${this.base}/files/${encodeURIComponent(name)}/info`);
  }

  renderFilePageBlob(name: string, page: number): Observable<string> {
    return this.http.get(`${this.base}/files/${encodeURIComponent(name)}/page/${page}`, { responseType: 'blob' })
      .pipe(map(blob => URL.createObjectURL(blob)));
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

  /** Retourne le premier asset signature actif avec son image en base64. */
  getMySignatureAsset(): Observable<{ id: string; base64: string } | null> {
    return this.getSignatureAssets().pipe(
      map((assets: any[]) => {
        const sig = assets.find(a => a.assetType === 'SIGNATURE' && a.active !== false);
        return sig ? { id: sig.id, base64: null as any } : null;
      }),
      switchMap((sig: any) => {
        if (!sig) return of(null);
        return this.http.get(`${this.base}/users/me/signature-assets/${sig.id}/image`,
          { responseType: 'blob' }).pipe(
          switchMap(blob => new Observable<{ id: string; base64: string }>(obs => {
            const reader = new FileReader();
            reader.onload = () => {
              const b64 = (reader.result as string).split(',')[1];
              obs.next({ id: sig.id, base64: b64 });
              obs.complete();
            };
            reader.readAsDataURL(blob);
          }))
        );
      })
    );
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
  renderPageBlob(docId: string, page: number): Observable<string> {
    return this.http.get(`${this.base}/pdf-documents/${docId}/pages/${page}/image`, { responseType: 'blob' })
      .pipe(map(blob => URL.createObjectURL(blob)));
  }
  renderPageWithZonesBlob(docId: string, page: number): Observable<string> {
    return this.http.get(`${this.base}/pdf-documents/${docId}/pages/${page}/image-with-zones`, { responseType: 'blob' })
      .pipe(map(blob => URL.createObjectURL(blob)));
  }
  finalizePdfDocument(docId: string): Observable<any> {
    return this.http.post(`${this.base}/pdf-documents/${docId}/finalize`, {});
  }
  downloadFinalPdf(docId: string): string {
    return `${this.base}/pdf-documents/${docId}/final`;
  }
  downloadFinalPdfBlob(docId: string, filename = 'document-final.pdf'): void {
    this.http.get(`${this.base}/pdf-documents/${docId}/final`, { responseType: 'blob' })
      .subscribe(blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url);
      });
  }
  deletePdfDocument(docId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/pdf-documents/${docId}`);
  }

  // Server-side preview / generation
  previewHtml(html: string): Observable<{ html: string }> {
    return this.http.post<{ html: string }>(`${this.base}/pdf/preview`, { html });
  }

  generatePdfBlob(html: string, css?: string): Observable<Blob> {
    return this.http.post(`${this.base}/pdf/generate`, { html, css: css || '' }, { responseType: 'blob' });
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


  // Paramètres — Postes (nouveau modèle)
  getPostes(activeOnly = false): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/parametres/postes`, { params: { activeOnly } });
  }
  createPoste(body: any): Observable<any> {
    return this.http.post(`${this.base}/parametres/postes`, body);
  }
  updatePoste(id: string, body: any): Observable<any> {
    return this.http.put(`${this.base}/parametres/postes/${id}`, body);
  }
  togglePoste(id: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/parametres/postes/${id}/toggle`, {});
  }
  deletePoste(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/parametres/postes/${id}`);
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

  // Types de documents (Paramètres)
  getTypeDocuments(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/type-documents`);
  }
  getTypeDocument(id: string): Observable<any> {
    return this.http.get(`${this.base}/type-documents/${id}`);
  }
  createTypeDocument(body: any): Observable<any> {
    return this.http.post(`${this.base}/type-documents`, body);
  }
  updateTypeDocument(id: string, body: any): Observable<any> {
    return this.http.put(`${this.base}/type-documents/${id}`, body);
  }
  toggleTypeDocument(id: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/type-documents/${id}/toggle`, {});
  }
  deleteTypeDocument(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/type-documents/${id}`);
  }
  convertTemplate(file: File): Observable<{ html: string; docxPath: string }> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ html: string; docxPath: string }>(
      `${this.base}/type-documents/convert-template`, form);
  }
  uploadTemplate(file: File): Observable<{ docxPath: string; pdfPath: string; fileName: string }> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ docxPath: string; pdfPath: string; fileName: string }>(
      `${this.base}/type-documents/upload-template`, form);
  }
  getTemplatePreviewBlob(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/type-documents/${id}/template-preview`,
      { responseType: 'blob' });
  }

  // Bureau Secrétaire
  getBureauDocuments(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/bureau/documents`);
  }
  uploadBureauDocument(file: File, type: string, titre?: string, destinataire?: string, typeDocumentId?: string, sourceInstructionId?: string): Observable<any> {
    const form = new FormData();
    form.append('file', file);
    form.append('type', type);
    if (titre) form.append('titre', titre);
    if (destinataire) form.append('destinataire', destinataire);
    if (typeDocumentId) form.append('typeDocumentId', typeDocumentId);
    if (sourceInstructionId) form.append('sourceInstructionId', sourceInstructionId);
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
  replaceBureauPdf(docId: string, file: File): Observable<any> {
    const form = new FormData();
    form.append('file', file);
    return this.http.put(`${this.base}/bureau/documents/${docId}/pdf`, form);
  }
  soumettreAuParapheur(docId: string, circuit?: string): Observable<any> {
    const params: Record<string, string> = {};
    if (circuit) params['circuit'] = circuit;
    return this.http.post(`${this.base}/bureau/documents/${docId}/soumettre`, {}, { params });
  }
  deleteBureauDocument(docId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/bureau/documents/${docId}`);
  }

  /** Crée un BureauDocument .docx en clonant le template du TypeDocument. */
  createFromTemplate(typeDocumentId: string, titre?: string, sourceInstructionId?: string): Observable<any> {
    const body: Record<string, string> = { typeDocumentId };
    if (titre) body['titre'] = titre;
    if (sourceInstructionId) body['sourceInstructionId'] = sourceInstructionId;
    return this.http.post(`${this.base}/bureau/documents/from-template`, body);
  }

  // ── WOPI / Collabora ─────────────────────────────────────────────────────
  /** Ouvre une session WOPI pour éditer un document au Bureau. */
  openBureauWopiSession(docId: string): Observable<any> {
    return this.http.get(`${this.base}/bureau/documents/${docId}/wopi-session`);
  }
  /** Ouvre une session WOPI pour réviser/signer un document au Parapheur. */
  openParapheurWopiSession(docId: string): Observable<any> {
    return this.http.get(`${this.base}/parapheur/${docId}/wopi-session`);
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
  renvoyerDocument(docId: string, comment: string): Observable<any> {
    return this.http.post(`${this.base}/parapheur/${docId}/renvoyer`, { comment });
  }
  repousserDocument(docId: string): Observable<any> {
    return this.http.post(`${this.base}/parapheur/${docId}/repousser`, {});
  }
  renvoyerProprietaireDocument(docId: string, comment: string): Observable<any> {
    return this.http.post(`${this.base}/parapheur/${docId}/renvoyer-proprietaire`, { comment });
  }
  getCircuitSignatures(docId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/parapheur/${docId}/circuit`);
  }
  /** Retrouve le .docx source d'un PdfDocument (pour ouvrir Collabora côté parapheur). */
  getSourceDocx(pdfDocId: string): Observable<{ bureauDocumentId: string | null; isDocx: boolean }> {
    return this.http.get<{ bureauDocumentId: string | null; isDocx: boolean }>(
      `${this.base}/parapheur/${pdfDocId}/source-docx`);
  }

  // ── Assistant IA ──────────────────────────────────────────────────────────

  /** Mode AUTO-APPLY ou SUGGESTION selon la configuration backend (ai.llm.auto-apply). */
  aiAssist(bureauDocumentId: string, action: string): Observable<any> {
    return this.http.post<any>(
      `${this.base}/ai/assist/${bureauDocumentId}`, null, { params: { action } });
  }

  /** Toujours en mode SUGGESTION : poste dans le fil d'instruction sans modifier le fichier. */
  aiSuggest(bureauDocumentId: string, action: string): Observable<any> {
    return this.http.post<any>(
      `${this.base}/ai/suggest/${bureauDocumentId}`, null, { params: { action } });
  }

  // ── Templates de participants (Paramètres > Types d'Instructions) ─────────

  getInstructionTypeParticipants(id: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/parametres/instruction-types/${id}/participants`);
  }

  saveInstructionTypeParticipants(id: string, participants: any[]): Observable<any[]> {
    return this.http.put<any[]>(
      `${this.base}/parametres/instruction-types/${id}/participants`, participants);
  }
  envoyerCorrection(docId: string, comment: string, audio?: File, highlights?: string): Observable<any> {
    const form = new FormData();
    form.append('comment', comment);
    if (audio) form.append('audio', audio);
    if (highlights) form.append('highlights', highlights);
    return this.http.post(`${this.base}/parapheur/${docId}/correction`, form);
  }

  // Classeurs Numériques
  getClasseurs(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/classeurs`);
  }
  createClasseur(body: any): Observable<any> {
    return this.http.post(`${this.base}/classeurs`, body);
  }
  deleteClasseur(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/classeurs/${id}`);
  }
  getClasseurDocuments(id: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/classeurs/${id}/documents`);
  }
  livrerCourrier(courrierDepartId: string, scan: File, classeurIds: string[]): Observable<any> {
    const form = new FormData();
    form.append('scan', scan);
    form.append('classeurIds', classeurIds.join(','));
    return this.http.post(`${this.base}/classeurs/livraison/${courrierDepartId}`, form);
  }
  livrerBureauDoc(bureauDocId: string, scan: File, classeurIds: string[]): Observable<any> {
    const form = new FormData();
    form.append('scan', scan);
    form.append('classeurIds', classeurIds.join(','));
    return this.http.post(`${this.base}/classeurs/livraison-bureau/${bureauDocId}`, form);
  }
  getScanUrl(key: string): string {
    return `${this.base}/classeurs/scan/${encodeURIComponent(key)}`;
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
