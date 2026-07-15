import type {ImgHTMLAttributes} from 'react';

type ImageProps = Omit<ImgHTMLAttributes<HTMLImageElement>, 'src'> & {
    src: string;
    alt: string;
    priority?: boolean;
};

export default function Image({priority = false, fetchPriority, alt, ...props}: ImageProps) {
    // This compatibility component keeps the existing image call sites usable in the static Vite build.
    // eslint-disable-next-line @next/next/no-img-element
    return <img {...props} alt={alt} fetchPriority={fetchPriority ?? (priority ? 'high' : undefined)}/>;
}
