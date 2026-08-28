export interface ExtractedInvoiceItem {
  productName: string;
  code: string;
  quantity: number;
  unit: string;
  unitPrice: number;
  totalPrice: number;
}

export interface ExtractedInvoicePayment {
  type: string;
  value: number;
}

export interface ExtractedInvoice {
  merchantName: string;
  cnpj: string;
  merchantAddress: string;
  totalItems: number;
  subtotal: number;
  discount: number;
  total: number;
  taxes: number;
  date: string;
  items: ExtractedInvoiceItem[];
  payments: ExtractedInvoicePayment[];
}

export type ExtractionStatus = 'READY' | 'BLOCKED' | 'TIMEOUT' | 'NAVIGATION_ERROR';

export interface ExtractionResult {
  status: ExtractionStatus;
  data?: ExtractedInvoice;
  message?: string;
}
